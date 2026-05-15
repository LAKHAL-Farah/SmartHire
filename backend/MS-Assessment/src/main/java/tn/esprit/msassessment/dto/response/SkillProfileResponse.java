package tn.esprit.msassessment.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Public skill profile for dashboards and downstream modules (roadmap, matching).
 */
public record SkillProfileResponse(
        int overallScore,
        List<DomainScoreItem> domains,
        List<String> strengths,
        List<String> weaknesses,
        Instant generatedAt,
        int version
) {
    public record DomainScoreItem(String code, String title, int scorePercent) {}
}
