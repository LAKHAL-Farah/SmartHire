package tn.esprit.msprofile.dto.response;

import java.time.Instant;
import java.util.UUID;

public record HireReadinessScoreResponse(
        UUID id,
        UUID userId,
        Integer cvScore,
        Integer linkedinScore,
        Integer githubScore,
        Integer globalScore,
        Instant computedAt
) {
}

