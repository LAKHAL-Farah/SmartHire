package tn.esprit.msassessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.dto.response.SkillProfileResponse;
import tn.esprit.msassessment.dto.response.SkillProfileResponse.DomainScoreItem;
import tn.esprit.msassessment.entity.AssessmentSession;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.entity.SkillProfile;
import tn.esprit.msassessment.entity.enums.SessionStatus;
import tn.esprit.msassessment.exception.ResourceNotFoundException;
import tn.esprit.msassessment.repository.AssessmentSessionRepository;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;
import tn.esprit.msassessment.repository.SkillProfileRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SkillProfileService {

    private final SkillProfileRepository skillProfileRepository;
    private final AssessmentSessionRepository sessionRepository;
    private final QuestionCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * Recomputes the profile from all <strong>published</strong> completed sessions for this user.
     * Call after submit (auto-release) or after admin publishes results.
     */
    public void refreshForUser(UUID userId) {
        String userIdStr = userId.toString();
        List<AssessmentSession> published =
                sessionRepository.findPublishedCompletedForUser(userIdStr, SessionStatus.COMPLETED);
        if (published.isEmpty()) {
            skillProfileRepository.deleteByUserId(userIdStr);
            return;
        }

        Map<String, Integer> bestScoreByCode = new HashMap<>();
        Map<String, String> titleByCode = new HashMap<>();
        for (AssessmentSession s : published) {
            QuestionCategory cat = s.getCategory();
            String code = cat.getCode();
            titleByCode.putIfAbsent(code, cat.getTitle());
            int sc = s.getScorePercent() != null ? s.getScorePercent() : 0;
            bestScoreByCode.merge(code, sc, Math::max);
        }

        int overall = (int)
                Math.round(bestScoreByCode.values().stream().mapToInt(i -> i).average().orElse(0));

        List<DomainScoreItem> domains = bestScoreByCode.entrySet().stream()
                .map(e -> new DomainScoreItem(
                        e.getKey(),
                        titleByCode.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue()))
                .sorted(Comparator.comparing(DomainScoreItem::title, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<Map.Entry<String, Integer>> sortedByScore = bestScoreByCode.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        List<String> strengths = sortedByScore.stream()
                .limit(3)
                .map(e -> formatDomainLine(e.getKey(), titleByCode, e.getValue()))
                .toList();

        List<String> weaknesses = buildWeaknesses(sortedByScore, titleByCode);

        SkillProfile row = skillProfileRepository
                .findByUserId(userIdStr)
                .orElseGet(() -> SkillProfile.builder().userId(userIdStr).version(0).build());

        try {
            row.setDomainScoresJson(objectMapper.writeValueAsString(bestScoreByCode));
            row.setStrengthsJson(objectMapper.writeValueAsString(strengths));
            row.setWeaknessesJson(objectMapper.writeValueAsString(weaknesses));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize skill profile JSON", e);
        }

        row.setOverallScore(overall);
        row.setGeneratedAt(Instant.now());
        row.setVersion(row.getVersion() + 1);
        skillProfileRepository.save(row);
    }

    @Transactional(readOnly = true)
    public SkillProfileResponse getForUser(UUID userId) {
        String userIdStr = userId.toString();
        SkillProfile row = skillProfileRepository
                .findByUserId(userIdStr)
                .orElseThrow(() -> new ResourceNotFoundException("No skill profile for user: " + userId));

        Map<String, Integer> scores = readJsonMap(row.getDomainScoresJson());
        List<DomainScoreItem> domains = scores.entrySet().stream()
                .map(e -> new DomainScoreItem(
                        e.getKey(),
                        resolveTitle(e.getKey()),
                        e.getValue()))
                .sorted(Comparator.comparing(DomainScoreItem::title, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<String> strengths = readStringList(row.getStrengthsJson());
        List<String> weaknesses = readStringList(row.getWeaknessesJson());

        return new SkillProfileResponse(
                row.getOverallScore(),
                domains,
                strengths,
                weaknesses,
                row.getGeneratedAt(),
                row.getVersion());
    }

    private String resolveTitle(String code) {
        return categoryRepository
                .findByCode(code)
                .map(QuestionCategory::getTitle)
                .orElse(code);
    }

    private static String formatDomainLine(String code, Map<String, String> titleByCode, int value) {
        String t = titleByCode.getOrDefault(code, code);
        return t + " (" + value + "%)";
    }

    private static List<String> buildWeaknesses(
            List<Map.Entry<String, Integer>> sortedByScoreDesc, Map<String, String> titleByCode) {
        // Show ONLY the worst scores (bottom 3), sorted ascending by score
        if (sortedByScoreDesc.size() <= 1) {
            return List.of();
        }
        return sortedByScoreDesc.stream()
                .sorted(Map.Entry.comparingByValue()) // Sort ascending (worst first)
                .limit(3) // Take only the 3 worst
                .map(e -> formatDomainLine(e.getKey(), titleByCode, e.getValue()))
                .toList();
    }

    private Map<String, Integer> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
