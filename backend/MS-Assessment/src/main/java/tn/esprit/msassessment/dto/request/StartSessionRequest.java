package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StartSessionRequest(
        @NotNull UUID userId,
        @NotNull Long categoryId,
        /** Optional: shown to admins (from client profile). Max 256 chars. */
        @Size(max = 256) String candidateDisplayName
) {}
