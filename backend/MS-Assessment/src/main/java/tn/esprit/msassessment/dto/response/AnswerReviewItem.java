package tn.esprit.msassessment.dto.response;

/**
 * One row in a scored session review (candidate after release, or admin anytime).
 */
public record AnswerReviewItem(
        Long questionId,
        String prompt,
        String difficulty,
        int questionPoints,
        Long selectedChoiceId,
        String selectedLabel,
        Long correctChoiceId,
        String correctLabel,
        boolean correct,
        int pointsEarned) {}
