package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record JobOfferRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String company,
        @NotBlank String rawDescription,
        String extractedKeywords,
        @Size(max = 512) String sourceUrl,
        Instant createdAt
) {
}

