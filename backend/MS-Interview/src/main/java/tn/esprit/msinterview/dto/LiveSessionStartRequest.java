package tn.esprit.msinterview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tn.esprit.msinterview.entity.enumerated.LiveSubMode;

public record LiveSessionStartRequest(
        @NotNull Long userId,
        @NotNull Long careerPathId,
        @NotNull LiveSubMode liveSubMode,
        @NotNull @Min(3) @Max(15) Integer questionCount,
        String companyName,
        String targetRole
) {
}
