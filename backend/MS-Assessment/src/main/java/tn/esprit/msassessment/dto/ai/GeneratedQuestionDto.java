package tn.esprit.msassessment.dto.ai;

import java.util.List;

/**
 * DTO for a generated question to send to frontend.
 */
public record GeneratedQuestionDto(
    String prompt,
    List<String> choices,
    int correctIndex,
    String difficulty,
    int points
) {
    public static GeneratedQuestionDto from(GeneratedQuestion question) {
        return new GeneratedQuestionDto(
            question.prompt(),
            question.choices(),
            question.correctIndex(),
            question.difficulty(),
            1  // default points
        );
    }
}
