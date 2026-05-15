package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record GitHubProfileRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 100) String githubUsername,
        @Min(0) @Max(100) Integer overallScore,
        @Min(0) Integer repoCount,
        String topLanguages,
        @Min(0) @Max(100) Integer profileReadmeScore,
        String feedback,
        ProcessingStatus auditStatus,
        @Size(max = 500) String auditErrorMessage,
        Instant createdAt,
        Instant analyzedAt
) {
}

