package tn.esprit.msassessment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitAnswersRequest(
        @NotEmpty @Valid List<AnswerSelection> selections,
        String notes
) {}
