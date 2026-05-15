package tn.esprit.msprofile.dto.response;

import java.time.Instant;
import java.util.UUID;

public record JobOfferResponse(
        UUID id,
        UUID userId,
        String title,
        String company,
        String rawDescription,
        String extractedKeywords,
        String sourceUrl,
        Instant createdAt
) {
}

