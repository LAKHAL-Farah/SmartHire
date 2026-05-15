package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.dto.request.AuditGitHubRequest;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.dto.response.GitHubRepoResponse;
import tn.esprit.msprofile.service.GitHubService;

import java.util.List;

/**
 * CORS is handled by the API Gateway.
 * Do not add @CrossOrigin here to avoid duplicate headers.
 */
@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GitHubController {

    private final GitHubService githubService;

    @PostMapping("/audit")
    public ResponseEntity<GitHubProfileResponse> audit(@Valid @RequestBody AuditGitHubRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(githubService.auditProfile(request.githubProfileUrl()));
    }

    @GetMapping
    public ResponseEntity<GitHubProfileResponse> getProfile() {
        return ResponseEntity.ok(githubService.getProfile());
    }

    @PostMapping("/reaudit")
    public ResponseEntity<GitHubProfileResponse> reaudit(@Valid @RequestBody AuditGitHubRequest request) {
        return ResponseEntity.ok(githubService.reauditProfile(request.githubProfileUrl()));
    }


    @GetMapping("/repositories")
    public ResponseEntity<List<GitHubRepoResponse>> getRepositories() {
        return ResponseEntity.ok(githubService.getRepositories());
    }
}
