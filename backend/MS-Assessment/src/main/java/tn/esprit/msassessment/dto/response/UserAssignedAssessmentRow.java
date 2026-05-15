package tn.esprit.msassessment.dto.response;

public record UserAssignedAssessmentRow(
        Long categoryId,
        String categoryCode,
        String categoryTitle,
        String status,
        boolean completed
) {}
