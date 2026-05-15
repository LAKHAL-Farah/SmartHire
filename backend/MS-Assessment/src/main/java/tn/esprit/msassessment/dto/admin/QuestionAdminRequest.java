package tn.esprit.msassessment.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionAdminRequest(
        @NotBlank @Size(max = 8000) String prompt,
        @NotNull @Min(1) Integer points,
        @NotBlank String difficulty,
        boolean active,
        /** Optional tag for topic-based quizzes (e.g. java, sql). */
        @Size(max = 128) String topic
) {}
