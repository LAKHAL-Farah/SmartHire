package tn.esprit.msassessment.dto.ai;

import java.util.List;

/**
 * Response containing generated questions.
 */
public record GenerateQuestionsResponse(
    Long categoryId,
    String categoryTitle,
    int generatedCount,
    List<GeneratedQuestionDto> questions,
    boolean success,
    String message
) {
    public static GenerateQuestionsResponse success(Long categoryId, String categoryTitle, List<GeneratedQuestionDto> questions) {
        return new GenerateQuestionsResponse(
            categoryId,
            categoryTitle,
            questions.size(),
            questions,
            true,
            "Successfully generated " + questions.size() + " questions"
        );
    }

    public static GenerateQuestionsResponse failure(Long categoryId, String message) {
        return new GenerateQuestionsResponse(
            categoryId,
            null,
            0,
            List.of(),
            false,
            message
        );
    }
}
