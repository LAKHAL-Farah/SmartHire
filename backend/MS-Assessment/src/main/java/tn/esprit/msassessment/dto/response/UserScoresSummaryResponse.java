package tn.esprit.msassessment.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Admin view: all completed assessment scores for a single user,
 * regardless of whether results have been published to the candidate.
 */
public record UserScoresSummaryResponse(
        String userId,
        String candidateDisplayName,
        String situation,
        String careerPath,
        int overallAvgScore,
        List<SessionScoreRow> sessions
) {
    public record SessionScoreRow(
            Long sessionId,
            String categoryTitle,
            String categoryCode,
            String topicTag,
            Integer scorePercent,
            boolean scoreReleased,
            boolean integrityViolation,
            Instant completedAt
    ) {}
}
