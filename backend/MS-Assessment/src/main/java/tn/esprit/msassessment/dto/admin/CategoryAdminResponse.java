package tn.esprit.msassessment.dto.admin;

public record CategoryAdminResponse(
        Long id,
        String code,
        String title,
        String description,
        long questionCount
) {}
