package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.dto.response.GitHubRepositoryResponse;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.GitHubProfileService;
import tn.esprit.msprofile.service.GitHubRepositoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/github-legacy")
@RequiredArgsConstructor
public class GitHubWorkflowController {

    private final GitHubProfileService gitHubProfileService;
    private final GitHubRepositoryService gitHubRepositoryService;
    private final StaticUserContext staticUserContext;

    @PostMapping("/audit")
    public ResponseEntity<GitHubProfileResponse> audit(@Valid @RequestBody AuditGitHubApiRequest request) {
        UUID userId = staticUserContext.getCurrentUserId();
        // A profile is "new" only when neither a profile exists for this user
        // nor a profile already exists for the requested GitHub username.
        boolean existsForUser = gitHubProfileService.profileExistsForUser(userId);
        boolean existsByUsername = gitHubProfileService.profileExistsByGithubUsername(request.githubUsername());
        boolean isNewProfile = !(existsForUser || existsByUsername);

        GitHubProfileResponse response = gitHubProfileService.auditGitHubProfile(userId, request.githubUsername());

        HttpStatus status = isNewProfile ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    public ResponseEntity<GitHubProfileResponse> getGitHubProfile() {
        return ResponseEntity.ok(gitHubProfileService.getGitHubProfileForUser(staticUserContext.getCurrentUserId()));
    }


    @PostMapping("/reaudit")
    public ResponseEntity<GitHubProfileResponse> reaudit() {
        return ResponseEntity.ok(gitHubProfileService.reauditProfile(staticUserContext.getCurrentUserId()));
    }

    @GetMapping("/repositories")
    public ResponseEntity<List<GitHubRepositoryResponse>> getAuditedRepositories() {
        GitHubProfileResponse profile = gitHubProfileService.getGitHubProfileForUser(staticUserContext.getCurrentUserId());
        return ResponseEntity.ok(gitHubRepositoryService.findByGithubProfileId(profile.id()));
    }

    public record AuditGitHubApiRequest(@NotBlank @Size(max = 100) String githubUsername) {
    }
}
