package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StartSessionByTopicRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 128) String topic,
        @Min(1) @Max(50) int count,
        @Size(max = 256) String candidateDisplayName
) {}
