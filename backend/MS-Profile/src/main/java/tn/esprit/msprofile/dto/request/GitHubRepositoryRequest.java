package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record GitHubRepositoryRequest(
        @NotNull UUID githubProfileId,
        @NotBlank @Size(max = 255) String repoName,
        @NotBlank @Size(max = 512) String repoUrl,
        @Size(max = 60) String language,
        @Min(0) Integer stars,
        @Min(0) Integer forksCount,
        Boolean isForked,
        @Min(0) @Max(100) Integer readmeScore,
        Boolean hasCiCd,
        Boolean hasTests,
        @Min(0) @Max(100) Integer codeStructureScore,
        String detectedIssues,
        Instant updatedAt,
        @Min(0) @Max(100) Integer overallScore
) {
}

