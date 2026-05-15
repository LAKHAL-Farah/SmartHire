package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkAssignAssessmentRequest(
        @NotEmpty(message = "At least one user is required") List<UUID> userIds,
        @NotEmpty(message = "At least one category is required") List<Long> categoryIds,
        boolean forceReassign
) {}
