package tn.esprit.msprofile.dto.response;

import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GitHubProfileResponse(
        UUID id,
        UUID userId,
        String githubUsername,
        String profileUrl,
        Integer overallScore,
        Integer repoCount,
        String topLanguages,
        Integer profileReadmeScore,
        String feedback,
        ProcessingStatus auditStatus,
        String auditErrorMessage,
        Instant createdAt,
        Instant analyzedAt,
        List<GitHubRepoResponse> repositories
) {
}

