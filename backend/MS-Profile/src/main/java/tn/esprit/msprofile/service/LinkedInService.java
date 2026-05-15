package tn.esprit.msprofile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.config.AppConstants;
import tn.esprit.msprofile.dto.LinkedInAlignmentResult;
import tn.esprit.msprofile.dto.LinkedInOptimizationResult;
import tn.esprit.msprofile.dto.LinkedInResponse;
import tn.esprit.msprofile.dto.LinkedInSectionScores;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ExternalApiException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.exception.ValidationException;
import tn.esprit.msprofile.mapper.LinkedInMapper;
import tn.esprit.msprofile.repository.JobOfferRepository;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedInService {

    private final LinkedInProfileRepository linkedInProfileRepository;
    private final NvidiaAiService nvidiaAiService;
    private final AuditLogService auditLogService;
    private final ProfileTipService profileTipService;
    private final JobOfferRepository jobOfferRepository;
    private final LinkedInMapper linkedInMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public LinkedInResponse analyzeProfile(
            String rawContent,
            String currentHeadline,
            String currentSummary,
            String currentSkills
    ) {
        UUID userId = AppConstants.currentUserId();
        LinkedInProfile entity = linkedInProfileRepository.findByUserId(userId).orElseGet(LinkedInProfile::new);
        entity.setUserId(userId);
        if (trimToNull(entity.getProfileUrl()) == null) {
            entity.setProfileUrl("about:blank");
        }
        entity.setRawContent(rawContent);
        entity.setCurrentHeadline(trimToNull(currentHeadline));
        entity.setCurrentSummary(trimToNull(currentSummary));
        entity.setCurrentSkills(trimToNull(currentSkills));
        entity.setScrapeStatus(ProcessingStatus.IN_PROGRESS);
        entity.setScrapeErrorMessage(null);
        entity = linkedInProfileRepository.save(entity);

        AuditLog logEntry = null;
        try {
            logEntry = auditLogService.logOperation(
                userId,
                OperationType.LINKEDIN_ANALYZE,
                "LinkedInProfile",
                entity.getId(),
                ProcessingStatus.IN_PROGRESS,
                null,
                null,
                null
            );

        } catch (Exception ex) {
            log.warn("Audit log insert failed for LinkedIn analyze {}: {}", entity.getId(), ex.getMessage());
        }

        long startedAt = System.currentTimeMillis();

        try {
            NvidiaAiService.AiResult scoreResult = nvidiaAiService.scoreLinkedInProfile(rawContent);
            LinkedInSectionScores sectionScores = objectMapper.readValue(scoreResult.content(), LinkedInSectionScores.class);

            entity.setGlobalScore(sectionScores.globalScore());
            entity.setSectionScoresJson(scoreResult.content());

            NvidiaAiService.AiResult optimizeResult = nvidiaAiService.optimizeLinkedInProfile(
                    rawContent,
                    currentHeadline,
                    currentSummary,
                    currentSkills
            );
            LinkedInOptimizationResult optimizationResult = objectMapper.readValue(optimizeResult.content(), LinkedInOptimizationResult.class);

            entity.setOptimizedHeadline(trimToNull(optimizationResult.optimizedHeadline()));
            entity.setOptimizedSummary(trimToNull(optimizationResult.optimizedSummary()));
            entity.setOptimizedSkills(objectMapper.writeValueAsString(optimizationResult.optimizedSkills()));
            entity.setScrapeStatus(ProcessingStatus.COMPLETED);
            entity.setAnalyzedAt(Instant.now());
            entity.setScrapeErrorMessage(null);
            entity = linkedInProfileRepository.save(entity);

                updateAuditLogSafely(
                    logEntry,
                    ProcessingStatus.COMPLETED,
                    scoreResult.tokensUsed() + optimizeResult.tokensUsed(),
                    (int) (System.currentTimeMillis() - startedAt),
                    null
                );

            try {
                profileTipService.generateTipsForLinkedIn(userId, entity.getId());
            } catch (Exception e) {
                log.warn("LinkedIn tip generation failed: {}", e.getMessage());
            }

            return linkedInMapper.toResponse(entity);
        } catch (ExternalApiException e) {
            log.warn("NVIDIA analysis unavailable, using deterministic fallback for LinkedIn profile {}: {}", entity.getId(), e.getMessage());

            LinkedInSectionScores fallbackScores = buildFallbackSectionScores(rawContent, currentHeadline, currentSummary, currentSkills);
            entity.setGlobalScore(fallbackScores.globalScore());
            entity.setSectionScoresJson(writeJsonSafely(fallbackScores));
            entity.setOptimizedHeadline(buildFallbackHeadline(currentHeadline));
            entity.setOptimizedSummary(buildFallbackSummary(currentSummary));
            entity.setOptimizedSkills(writeJsonSafely(buildFallbackSkills(currentSkills)));
            entity.setScrapeStatus(ProcessingStatus.COMPLETED);
            entity.setAnalyzedAt(Instant.now());
            entity.setScrapeErrorMessage("AI provider unavailable. Generated fallback optimization.");
            entity = linkedInProfileRepository.save(entity);

                updateAuditLogSafely(
                    logEntry,
                    ProcessingStatus.COMPLETED,
                    0,
                    (int) (System.currentTimeMillis() - startedAt),
                    truncate(e.getMessage(), 500)
                );

            return linkedInMapper.toResponse(entity);
        } catch (Exception e) {
            entity.setScrapeStatus(ProcessingStatus.FAILED);
            entity.setScrapeErrorMessage(truncate(e.getMessage(), 500));
            linkedInProfileRepository.save(entity);

                updateAuditLogSafely(
                    logEntry,
                    ProcessingStatus.FAILED,
                    null,
                    (int) (System.currentTimeMillis() - startedAt),
                    truncate(e.getMessage(), 500)
                );
            throw new ExternalApiException("LinkedIn analysis failed", e);
        }
    }

    @Transactional(readOnly = true)
    public LinkedInResponse getProfile() {
        UUID userId = AppConstants.currentUserId();
        return linkedInProfileRepository.findByUserId(userId)
            .map(linkedInMapper::toResponse)
            .orElseGet(() -> emptyProfileResponse(userId));
    }

    @Transactional
    public LinkedInResponse alignToJobOffer(UUID jobOfferId) {
        UUID userId = AppConstants.currentUserId();
        LinkedInProfile entity = linkedInProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No LinkedIn profile found — paste your profile content to get started"));

        if (entity.getScrapeStatus() != ProcessingStatus.COMPLETED) {
            throw new ValidationException("LinkedIn profile must be analyzed first");
        }

        JobOffer jobOffer = jobOfferRepository.findByIdAndUserId(jobOfferId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job offer not found"));

        List<String> keywords = parseKeywords(jobOffer.getExtractedKeywords());
        if (keywords.isEmpty()) {
            throw new ValidationException("Job offer keywords not extracted");
        }

        try {
            NvidiaAiService.AiResult alignmentResultRaw = nvidiaAiService.alignLinkedInToJobOffer(
                    entity.getCurrentHeadline(),
                    entity.getCurrentSummary(),
                    entity.getCurrentSkills(),
                    jobOffer.getRawDescription(),
                    keywords
            );
            LinkedInAlignmentResult alignmentResult = objectMapper.readValue(alignmentResultRaw.content(), LinkedInAlignmentResult.class);

            entity.setJobAlignedHeadline(trimToNull(alignmentResult.alignedHeadline()));
            entity.setJobAlignedSummary(trimToNull(alignmentResult.alignedSummary()));
            entity.setJobAlignedSkills(objectMapper.writeValueAsString(alignmentResult.alignedSkills()));
            entity.setAlignedJobOfferId(jobOffer.getId());
            entity = linkedInProfileRepository.save(entity);

            return linkedInMapper.toResponse(entity);
        } catch (ExternalApiException e) {
            log.warn("LinkedIn alignment AI unavailable, using fallback for profile {} and job {}: {}",
                    entity.getId(), jobOffer.getId(), e.getMessage());

            entity.setJobAlignedHeadline(buildFallbackAlignedHeadline(entity.getCurrentHeadline(), jobOffer.getTitle(), keywords));
            entity.setJobAlignedSummary(buildFallbackAlignedSummary(entity.getCurrentSummary(), jobOffer.getRawDescription(), keywords));
            entity.setJobAlignedSkills(writeJsonSafely(buildFallbackAlignedSkills(entity.getCurrentSkills(), keywords)));
            entity.setAlignedJobOfferId(jobOffer.getId());
            entity = linkedInProfileRepository.save(entity);
            return linkedInMapper.toResponse(entity);
        } catch (Exception e) {
            throw new ExternalApiException("LinkedIn alignment failed", e);
        }
    }

    private List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.isBlank()) {
            return List.of();
        }

        try {
            List<String> keywords = objectMapper.readValue(rawKeywords, new TypeReference<>() {
            });
            if (keywords == null) {
                return List.of();
            }
            return keywords.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void updateAuditLogSafely(
            AuditLog logEntry,
            ProcessingStatus status,
            Integer tokensUsed,
            Integer durationMs,
            String errorMessage
    ) {
        if (logEntry == null) {
            return;
        }
        try {
            auditLogService.updateLog(logEntry.getId(), status, tokensUsed, durationMs, errorMessage);
        } catch (Exception ex) {
            log.warn("Audit log update failed for {}: {}", logEntry.getId(), ex.getMessage());
        }
    }

    private LinkedInResponse emptyProfileResponse(UUID userId) {
        return new LinkedInResponse(
                null,
                userId,
                null,
                ProcessingStatus.PENDING,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private LinkedInSectionScores buildFallbackSectionScores(
            String rawContent,
            String currentHeadline,
            String currentSummary,
            String currentSkills
    ) {
        int headline = clamp(containsText(currentHeadline) ? 70 : 20);
        int summaryLength = safe(currentSummary).length();
        int summary = clamp(summaryLength >= 300 ? 75 : summaryLength >= 120 ? 60 : summaryLength >= 40 ? 45 : 20);
        int skillsCount = buildFallbackSkills(currentSkills).size();
        int skills = clamp(skillsCount >= 15 ? 78 : skillsCount >= 8 ? 62 : skillsCount >= 3 ? 45 : 20);
        int recommendations = clamp(safe(rawContent).toLowerCase().contains("recommend") ? 55 : 20);
        int global = clamp((headline + summary + skills + recommendations) / 4);

        return new LinkedInSectionScores(
                headline,
                summary,
                skills,
                recommendations,
                global,
                "Add a role + specialization + value proposition format to your headline.",
                "Expand your About section with outcomes and measurable impact.",
                "Prioritize 15 to 25 role-relevant skills and keep naming consistent.",
                "Request written recommendations from recent peers or managers."
        );
    }

    private String buildFallbackHeadline(String currentHeadline) {
        String headline = trimToNull(currentHeadline);
        if (headline == null) {
            return "Professional | Core Skills | Value Delivered";
        }

        String normalized = headline.replaceAll("\\s+", " ").trim();
        if (!normalized.contains("|")) {
            normalized = normalized + " | Delivering scalable, business-focused solutions";
        }
        return normalized.length() <= 220 ? normalized : normalized.substring(0, 220);
    }

    private String buildFallbackSummary(String currentSummary) {
        String summary = trimToNull(currentSummary);
        if (summary == null) {
            return "Experienced professional focused on delivering measurable impact through collaboration, technical depth, and continuous improvement.";
        }

        String condensed = summary.replaceAll("\\s+", " ").trim();
        return "I build reliable products with a focus on measurable outcomes and maintainable delivery. "
                + condensed
                + " I am currently focused on roles where I can combine technical execution, clear communication, and business impact.";
    }

    private List<String> buildFallbackSkills(String currentSkills) {
        String raw = safe(currentSkills);
        if (raw.isBlank()) {
            return List.of("Communication", "Problem Solving", "Team Collaboration");
        }

        String[] parts = raw.split("[\\n,;]");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isBlank()) {
                unique.add(trimmed);
            }
            if (unique.size() == 25) {
                break;
            }
        }

        if (unique.isEmpty()) {
            return List.of("Communication", "Problem Solving", "Team Collaboration");
        }

        unique.add("Stakeholder Communication");
        unique.add("Delivery Ownership");
        return new ArrayList<>(unique);
    }

    private String buildFallbackAlignedHeadline(String currentHeadline, String jobTitle, List<String> keywords) {
        String baseHeadline = trimToNull(currentHeadline);
        if (baseHeadline == null) {
            baseHeadline = "Professional";
        }

        String role = trimToNull(jobTitle);
        if (role == null) {
            role = "Target Role";
        }

        List<String> topKeywords = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .limit(3)
                .toList();

        String keywordPart = topKeywords.isEmpty() ? "Role Alignment" : String.join(" · ", topKeywords);
        String aligned = role + " | " + keywordPart + " | " + baseHeadline;
        return aligned.length() <= 220 ? aligned : aligned.substring(0, 220);
    }

    private String buildFallbackAlignedSummary(String currentSummary, String jobDescription, List<String> keywords) {
        String summary = trimToNull(currentSummary);
        if (summary == null) {
            summary = "I focus on delivering reliable, high-impact outcomes with clear communication and ownership.";
        }

        String descriptionPreview = trimToNull(jobDescription);
        if (descriptionPreview != null && descriptionPreview.length() > 200) {
            descriptionPreview = descriptionPreview.substring(0, 200);
        }

        String keywordLine = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .limit(6)
                .reduce((a, b) -> a + ", " + b)
                .orElse("key role priorities");

        return "Aligned for this opportunity with emphasis on " + keywordLine + ". "
                + summary
                + (descriptionPreview == null ? ""
                : " The target role highlights: " + descriptionPreview + ".");
    }

    private List<String> buildFallbackAlignedSkills(String currentSkills, List<String> jobKeywords) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        for (String keyword : jobKeywords) {
            if (keyword != null) {
                String trimmed = keyword.trim();
                if (!trimmed.isBlank()) {
                    merged.add(trimmed);
                }
            }
            if (merged.size() >= 12) {
                break;
            }
        }

        for (String skill : buildFallbackSkills(currentSkills)) {
            merged.add(skill);
            if (merged.size() >= 20) {
                break;
            }
        }

        return new ArrayList<>(merged);
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ExternalApiException("Failed to serialize LinkedIn response", e);
        }
    }

    private boolean containsText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
