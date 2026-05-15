package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.msprofile.exception.ExternalApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AtsScoreService {

    private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9\\s]");

    private final ObjectMapper objectMapper;
    private final OpenAiService openAiService;

    public AtsScoreService(ObjectMapper objectMapper, OpenAiService openAiService) {
        this.objectMapper = objectMapper;
        this.openAiService = openAiService;
    }

    public int computeScore(String cvJson, String jobDescription, List<String> jobKeywords) {
        return computeFullAnalysis(cvJson, jobDescription, jobKeywords).overallScore();
    }

    public AtsAnalysisResult computeFullAnalysis(String cvJson, String jobDescription, List<String> jobKeywords) {
        OpenAiService.AiResult result = openAiService.computeAtsScore(cvJson, jobDescription, jobKeywords);

        try {
            JsonNode root = objectMapper.readTree(result.content());
            JsonNode breakdown = root.path("breakdown");

            int overallScore = clampScore(root.path("overallScore").asInt(0));
            int keywordMatchScore = clampScore(breakdown.path("keywordMatch").asInt(0));
            int experienceRelevanceScore = clampScore(breakdown.path("experienceRelevance").asInt(0));
            int skillsAlignmentScore = clampScore(breakdown.path("skillsAlignment").asInt(0));
            int educationScore = clampScore(breakdown.path("educationCertifications").asInt(0));

            List<String> matchedKeywords = readStringArray(root.path("matchedKeywords"));
            List<String> missingKeywords = readStringArray(root.path("missingKeywords"));
            String strengthSummary = root.path("strengthSummary").asText("");
            List<String> improvementSuggestions = readStringArray(root.path("improvementSuggestions"));

            return new AtsAnalysisResult(
                    overallScore,
                    keywordMatchScore,
                    experienceRelevanceScore,
                    skillsAlignmentScore,
                    educationScore,
                    matchedKeywords,
                    missingKeywords,
                    strengthSummary,
                    improvementSuggestions,
                    result.content(),
                    result.tokensUsed()
            );
        } catch (Exception e) {
            throw new ExternalApiException("ATS scoring returned invalid format", e);
        }
    }


    public BigDecimal computeKeywordMatchRate(String cvJson, List<String> jobKeywords) {
        return computeMatchRatio(cvJson, jobKeywords);
    }

    private BigDecimal computeMatchRatio(String cvParsedContentJson, List<String> jobKeywords) {
        if (jobKeywords == null || jobKeywords.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String cvText = extractText(cvParsedContentJson);
        Set<String> cvTokens = tokenize(cvText);

        int matchCount = 0;
        for (String keyword : jobKeywords) {
            String normalizedKeyword = normalize(keyword);
            if (normalizedKeyword.isBlank()) {
                continue;
            }
            if (matches(cvTokens, cvText, normalizedKeyword)) {
                matchCount++;
            }
        }

        BigDecimal ratio = BigDecimal.valueOf(matchCount)
                .divide(BigDecimal.valueOf(jobKeywords.size()), 4, RoundingMode.HALF_UP);
        return ratio.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean matches(Set<String> cvTokens, String cvText, String normalizedKeyword) {
        if (normalizedKeyword.contains(" ")) {
            return cvText.contains(normalizedKeyword);
        }
        return cvTokens.contains(normalizedKeyword);
    }

    private String extractText(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            List<String> textParts = new ArrayList<>();
            collectText(root, textParts);
            return normalize(String.join(" ", textParts));
        } catch (Exception ignored) {
            // Parsed content may occasionally be plain text in MVP migration data.
            return normalize(json);
        }
    }

    private void collectText(JsonNode node, List<String> acc) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            acc.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectText(child, acc));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), acc));
        }
    }

    private Set<String> tokenize(String text) {
        if (text.isBlank()) {
            return Set.of();
        }
        return List.of(text.split("\\s+")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT);
        String noPunctuation = PUNCTUATION.matcher(lower).replaceAll(" ");
        return noPunctuation.trim().replaceAll("\\s+", " ");
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }
}
