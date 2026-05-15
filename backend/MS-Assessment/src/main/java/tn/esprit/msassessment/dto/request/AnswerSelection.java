package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotNull;

public record AnswerSelection(
        @NotNull Long questionId,
        @NotNull Long answerChoiceId
) {}
