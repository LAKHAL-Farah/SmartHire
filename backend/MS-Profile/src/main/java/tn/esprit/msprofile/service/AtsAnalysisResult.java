package tn.esprit.msprofile.service;

import java.util.List;

public record AtsAnalysisResult(
        int overallScore,
        int keywordMatchScore,
        int experienceRelevanceScore,
        int skillsAlignmentScore,
        int educationScore,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        String strengthSummary,
        List<String> improvementSuggestions,
        String rawJson,
        int tokensUsed
) {
}
