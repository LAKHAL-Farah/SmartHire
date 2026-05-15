package tn.esprit.msprofile.dto.response;

import java.util.UUID;

public record GitHubRepoResponse(
        UUID id,
        String repoName,
        String repoUrl,
        String description,
        String language,
        Integer stars,
        Integer forksCount,
        Boolean isForked,
        Boolean isArchived,
        String pushedAt,
        Integer readmeScore,
        Boolean hasCiCd,
        Boolean hasTests,
        Integer codeStructureScore,
        String auditFeedback,
        String fixSuggestions,
        Integer overallScore
) {
}
