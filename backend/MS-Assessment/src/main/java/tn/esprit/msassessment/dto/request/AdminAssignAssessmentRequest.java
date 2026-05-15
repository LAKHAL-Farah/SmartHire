package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AdminAssignAssessmentRequest(
        @NotNull(message = "User ID is required") UUID userId,
        @NotEmpty(message = "At least one category is required") List<Long> categoryIds,
        String situation,
        String careerPath,
        boolean forceReassign
) {}
