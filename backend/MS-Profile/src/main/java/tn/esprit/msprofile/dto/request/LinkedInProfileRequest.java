package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record LinkedInProfileRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 512) String profileUrl,
        String rawContent,
        ProcessingStatus scrapeStatus,
        @Size(max = 500) String scrapeErrorMessage,
        @Min(0) @Max(100) Integer globalScore,
        String sectionScoresJson,
        Instant createdAt,
        @Size(max = 220) String currentHeadline,
        @Size(max = 220) String optimizedHeadline,
        String currentSummary,
        String optimizedSummary,
        String optimizedSkills,
        Instant analyzedAt
) {
}

