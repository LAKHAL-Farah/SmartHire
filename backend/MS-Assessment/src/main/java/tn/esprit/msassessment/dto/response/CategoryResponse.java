package tn.esprit.msassessment.dto.response;

public record CategoryResponse(
        Long id,
        String code,
        String title,
        String description
) {}
