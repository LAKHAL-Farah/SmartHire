package tn.esprit.msassessment.dto.admin;

import tn.esprit.msassessment.entity.enums.DifficultyLevel;

import java.util.List;

public record QuestionAdminResponse(
        Long id,
        Long categoryId,
        String prompt,
        int points,
        DifficultyLevel difficulty,
        boolean active,
        String topic,
        List<ChoiceAdminResponse> choices
) {}
