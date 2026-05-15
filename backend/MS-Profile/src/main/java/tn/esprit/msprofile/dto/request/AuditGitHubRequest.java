package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuditGitHubRequest(
        @NotBlank String githubProfileUrl
) {
}
