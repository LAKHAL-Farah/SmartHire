package tn.esprit.msprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.LinkedInProfileRequest;
import tn.esprit.msprofile.dto.response.LinkedInProfileResponse;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.DuplicateResourceException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedInProfileService extends AbstractCrudService<LinkedInProfile, LinkedInProfileResponse> {

    private final LinkedInProfileRepository linkedInProfileRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Value("${app.async-processing.enabled:true}")
    private boolean asyncProcessingEnabled;


    @Override
    protected JpaRepository<LinkedInProfile, UUID> repository() {
        return linkedInProfileRepository;
    }

    @Override
    protected LinkedInProfileResponse toResponse(LinkedInProfile entity) {
        return new LinkedInProfileResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getProfileUrl(),
                entity.getRawContent(),
                entity.getScrapeStatus(),
                entity.getScrapeErrorMessage(),
                entity.getGlobalScore(),
                entity.getSectionScoresJson(),
                entity.getCreatedAt(),
                entity.getCurrentHeadline(),
                entity.getOptimizedHeadline(),
                entity.getCurrentSummary(),
                entity.getOptimizedSummary(),
                entity.getOptimizedSkills(),
                entity.getAnalyzedAt()
        );
    }

    @Override
    protected String resourceName() {
        return "LinkedInProfile";
    }

    @Transactional
    public LinkedInProfileResponse analyzeLinkedInProfile(UUID userId, String profileUrl) {
        long startedAt = System.currentTimeMillis();
        LinkedInProfile profile = linkedInProfileRepository.findByUserId(userId).orElseGet(LinkedInProfile::new);
        profile.setUserId(userId);
        profile.setProfileUrl(profileUrl.trim());
        profile.setScrapeStatus(ProcessingStatus.IN_PROGRESS);
        profile.setScrapeErrorMessage(null);
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(Instant.now());
        }
        profile = linkedInProfileRepository.save(profile);

        try {
            String rawContent = scrapeProfile(profileUrl);
            LinkedInScoreBreakdown scoreBreakdown = scoreProfile(rawContent);

            profile.setRawContent(rawContent);
            profile.setGlobalScore(scoreBreakdown.globalScore());
            profile.setSectionScoresJson(toJson(scoreBreakdown.sectionScores()));
            profile.setScrapeStatus(ProcessingStatus.COMPLETED);
            profile.setScrapeErrorMessage(null);
            profile.setAnalyzedAt(Instant.now());

            LinkedInProfile saved = linkedInProfileRepository.save(profile);
                auditLogService.logOperation(
                    userId,
                    OperationType.LINKEDIN_SCRAPE,
                    "LinkedInProfile",
                    saved.getId(),
                    ProcessingStatus.COMPLETED,
                    900,
                    (int) (System.currentTimeMillis() - startedAt),
                    null
                );
                auditLogService.logOperation(
                    userId,
                    OperationType.LINKEDIN_ANALYZE,
                    "LinkedInProfile",
                    saved.getId(),
                    ProcessingStatus.COMPLETED,
                    1300,
                    (int) (System.currentTimeMillis() - startedAt),
                    null
                );
            UUID profileId = saved.getId();
            if (asyncProcessingEnabled) {
                CompletableFuture.runAsync(() -> generateOptimizedContent(profileId));
            } else {
                generateOptimizedContent(profileId);
            }
            return toResponse(saved);
        } catch (Exception e) {
            profile.setScrapeStatus(ProcessingStatus.FAILED);
            profile.setScrapeErrorMessage(truncate(e.getMessage(), 500));
            linkedInProfileRepository.save(profile);
                auditLogService.logOperation(
                    userId,
                    OperationType.LINKEDIN_SCRAPE,
                    "LinkedInProfile",
                    profile.getId(),
                    ProcessingStatus.FAILED,
                    150,
                    (int) (System.currentTimeMillis() - startedAt),
                    e.getMessage()
                );
            return toResponse(profile);
        }
    }

    public String scrapeProfile(String profileUrl) {
        try {
            Document document = Jsoup.connect(profileUrl)
                    .userAgent("Mozilla/5.0 (compatible; SmartHireBot/1.0)")
                    .timeout(10000)
                    .get();
            return document.text();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scrape LinkedIn profile content", e);
        }
    }

    public LinkedInScoreBreakdown scoreProfile(String rawContent) {
        String safeContent = rawContent == null ? "" : rawContent;
        int wordCount = safeContent.isBlank() ? 0 : safeContent.split("\\s+").length;

        int headlineScore = Math.min(100, 40 + Math.min(60, estimateHeadline(safeContent).length()));
        int summaryScore = Math.min(100, (int) Math.round(Math.min(1.0, wordCount / 250.0) * 100));
        int skillsScore = Math.min(100, extractSkillsFromText(safeContent).size() * 12);
        int recommendationsScore = Math.min(100, countOccurrences(safeContent.toLowerCase(Locale.ROOT), "recommend") * 15 + 30);

        int global = (int) Math.round((headlineScore * 0.25) + (summaryScore * 0.35) + (skillsScore * 0.25) + (recommendationsScore * 0.15));

        Map<String, Integer> sectionScores = new LinkedHashMap<>();
        sectionScores.put("headline", headlineScore);
        sectionScores.put("summary", summaryScore);
        sectionScores.put("skills", skillsScore);
        sectionScores.put("recommendations", recommendationsScore);

        return new LinkedInScoreBreakdown(headlineScore, summaryScore, skillsScore, recommendationsScore, global, sectionScores);
    }

    @Transactional
    public void generateOptimizedContent(UUID profileId) {
        LinkedInProfile profile = linkedInProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedInProfile not found with id=" + profileId));

        if (profile.getRawContent() == null || profile.getRawContent().isBlank()) {
            return;
        }

        String raw = profile.getRawContent();
        List<String> extractedSkills = extractSkillsFromText(raw);
        String currentHeadline = estimateHeadline(raw);
        String currentSummary = estimateSummary(raw);

        profile.setCurrentHeadline(currentHeadline);
        profile.setOptimizedHeadline(buildOptimizedHeadline(currentHeadline, extractedSkills));
        profile.setCurrentSummary(currentSummary);
        profile.setOptimizedSummary(buildOptimizedSummary(currentSummary, extractedSkills));
        profile.setOptimizedSkills(String.join(", ", extractedSkills));
        profile.setAnalyzedAt(Instant.now());

        linkedInProfileRepository.save(profile);
    }

    public LinkedInProfileResponse getLinkedInProfileForUser(UUID userId) {
        return toResponse(linkedInProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedInProfile not found for userId=" + userId)));
    }

    @Transactional
    public LinkedInProfileResponse reanalyzeProfile(UUID userId) {
        LinkedInProfile profile = linkedInProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedInProfile not found for userId=" + userId));
        return analyzeLinkedInProfile(userId, profile.getProfileUrl());
    }

    public LinkedInProfileResponse findByUserId(UUID userId) {
        return toResponse(linkedInProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new tn.esprit.msprofile.exception.ResourceNotFoundException("LinkedInProfile not found for userId=" + userId)));
    }

    @Transactional
    public LinkedInProfileResponse create(LinkedInProfileRequest request) {
        validateUserUniqueness(request.userId(), null);
        LinkedInProfile entity = new LinkedInProfile();
        apply(entity, request);
        return toResponse(linkedInProfileRepository.save(entity));
    }

    @Transactional
    public LinkedInProfileResponse update(UUID id, LinkedInProfileRequest request) {
        LinkedInProfile entity = requireEntity(id);
        validateUserUniqueness(request.userId(), id);
        apply(entity, request);
        return toResponse(linkedInProfileRepository.save(entity));
    }

    private void validateUserUniqueness(UUID userId, UUID currentId) {
        linkedInProfileRepository.findByUserId(userId).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new DuplicateResourceException("A LinkedInProfile already exists for userId=" + userId);
            }
        });
    }

    private void apply(LinkedInProfile entity, LinkedInProfileRequest request) {
        entity.setUserId(request.userId());
        entity.setProfileUrl(request.profileUrl().trim());
        entity.setRawContent(trimToNull(request.rawContent()));
        entity.setScrapeStatus(request.scrapeStatus() != null ? request.scrapeStatus() : ProcessingStatus.PENDING);
        entity.setScrapeErrorMessage(trimToNull(request.scrapeErrorMessage()));
        entity.setGlobalScore(request.globalScore());
        entity.setSectionScoresJson(trimToNull(request.sectionScoresJson()));
        entity.setCreatedAt(request.createdAt() != null ? request.createdAt() : entity.getCreatedAt());
        entity.setCurrentHeadline(trimToNull(request.currentHeadline()));
        entity.setOptimizedHeadline(trimToNull(request.optimizedHeadline()));
        entity.setCurrentSummary(trimToNull(request.currentSummary()));
        entity.setOptimizedSummary(trimToNull(request.optimizedSummary()));
        entity.setOptimizedSkills(trimToNull(request.optimizedSkills()));
        entity.setAnalyzedAt(request.analyzedAt());
    }

    private String estimateHeadline(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }
        String sentence = rawContent.split("[.!?\\n]")[0].trim();
        if (sentence.length() > 120) {
            return sentence.substring(0, 120).trim();
        }
        return sentence;
    }

    private String estimateSummary(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }
        String normalized = rawContent.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 700 ? normalized : normalized.substring(0, 700).trim();
    }

    private List<String> extractSkillsFromText(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }

        List<String> commonSkills = List.of(
                "java", "spring", "sql", "python", "docker", "kubernetes", "react", "angular",
                "javascript", "typescript", "aws", "azure", "microservices", "git", "ci", "cd"
        );

        String normalized = rawContent.toLowerCase(Locale.ROOT);
        List<String> detected = commonSkills.stream()
                .filter(normalized::contains)
                .map(skill -> skill.toUpperCase(Locale.ROOT))
                .toList();

        if (!detected.isEmpty()) {
            return detected;
        }

        return Arrays.stream(normalized.replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 5)
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(entry -> entry.getKey().toUpperCase(Locale.ROOT))
                .toList();
    }

    private String buildOptimizedHeadline(String currentHeadline, List<String> skills) {
        String base = currentHeadline == null || currentHeadline.isBlank()
                ? "Results-driven Software Engineer"
                : currentHeadline;

        if (skills.isEmpty()) {
            return base;
        }
        return base + " | " + String.join(" | ", skills.stream().limit(3).toList());
    }

    private String buildOptimizedSummary(String currentSummary, List<String> skills) {
        String base = currentSummary == null || currentSummary.isBlank()
                ? "Engineer focused on delivering scalable and measurable business impact."
                : currentSummary;

        String skillsText = skills.isEmpty() ? "cross-functional delivery" : String.join(", ", skills.stream().limit(6).toList());
        return base + " Core strengths include " + skillsText + ".";
    }

    private int countOccurrences(String text, String target) {
        if (text == null || text.isBlank() || target == null || target.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize LinkedIn score breakdown", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record LinkedInScoreBreakdown(
            int headlineScore,
            int summaryScore,
            int skillsScore,
            int recommendationsScore,
            int globalScore,
            Map<String, Integer> sectionScores
    ) {
    }
}

