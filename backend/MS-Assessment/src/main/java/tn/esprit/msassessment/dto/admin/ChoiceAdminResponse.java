package tn.esprit.msassessment.dto.admin;

public record ChoiceAdminResponse(
        Long id,
        String label,
        boolean correct,
        int sortOrder
) {}
