package tn.esprit.msprofile.dto.response;

import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record LinkedInProfileResponse(
        UUID id,
        UUID userId,
        String profileUrl,
        String rawContent,
        ProcessingStatus scrapeStatus,
        String scrapeErrorMessage,
        Integer globalScore,
        String sectionScoresJson,
        Instant createdAt,
        String currentHeadline,
        String optimizedHeadline,
        String currentSummary,
        String optimizedSummary,
        String optimizedSkills,
        Instant analyzedAt
) {
}

