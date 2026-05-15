package tn.esprit.msprofile.dto;

import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record LinkedInResponse(
        UUID id,
        UUID userId,
        String rawContent,
        ProcessingStatus analysisStatus,
        String scrapeErrorMessage,
        Integer globalScore,
        String sectionScoresJson,
        String currentHeadline,
        String optimizedHeadline,
        String currentSummary,
        String optimizedSummary,
        String currentSkills,
        String optimizedSkills,
        String jobAlignedHeadline,
        String jobAlignedSummary,
        String jobAlignedSkills,
        UUID alignedJobOfferId,
        Instant createdAt,
        Instant analyzedAt

) {
}
