package tn.esprit.msprofile.dto.response;

import java.util.UUID;

public record GitHubRepositoryResponse(
        UUID id,
        UUID githubProfileId,
        String repoName,
        String repoUrl,
        String language,
        Integer stars,
        Integer forksCount,
        Boolean isForked,
        Integer readmeScore,
        Boolean hasCiCd,
        Boolean hasTests,
        Integer codeStructureScore,
        String detectedIssues,
        java.time.Instant updatedAt,
        Integer overallScore
) {
}

