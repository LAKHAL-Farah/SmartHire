package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record HireReadinessScoreRequest(
        @NotNull UUID userId,
        @Min(0) @Max(100) Integer cvScore,
        @Min(0) @Max(100) Integer linkedinScore,
        @Min(0) @Max(100) Integer githubScore,
        @Min(0) @Max(100) Integer globalScore,
        Instant computedAt
) {
}

