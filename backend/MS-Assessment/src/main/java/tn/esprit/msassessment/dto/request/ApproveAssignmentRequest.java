package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ApproveAssignmentRequest(
        @NotEmpty(message = "Select at least one category") List<Long> categoryIds
) {}
