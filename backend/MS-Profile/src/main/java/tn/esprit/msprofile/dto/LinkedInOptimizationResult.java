package tn.esprit.msprofile.dto;

import java.util.List;

public record LinkedInOptimizationResult(
        String optimizedHeadline,
        String optimizedSummary,
        List<String> optimizedSkills,
        String optimizationRationale,
        int projectedScoreImprovement

) {
}
