package tn.esprit.msassessment.dto.response;

import tn.esprit.msassessment.entity.enums.DifficultyLevel;

import java.util.List;

public record QuestionPaperItem(
        Long id,
        String prompt,
        DifficultyLevel difficulty,
        int points,
        List<ChoiceView> choices
) {}
