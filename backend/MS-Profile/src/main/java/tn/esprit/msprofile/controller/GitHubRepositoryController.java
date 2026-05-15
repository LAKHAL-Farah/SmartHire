package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.GitHubRepositoryRequest;
import tn.esprit.msprofile.dto.response.GitHubRepositoryResponse;
import tn.esprit.msprofile.service.GitHubRepositoryService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github-repositories")
@RequiredArgsConstructor
public class GitHubRepositoryController {

    private final GitHubRepositoryService gitHubRepositoryService;

    @GetMapping
    public ResponseEntity<List<GitHubRepositoryResponse>> findAll() {
        return ResponseEntity.ok(gitHubRepositoryService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GitHubRepositoryResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(gitHubRepositoryService.findById(id));
    }

    @GetMapping("/profile/{githubProfileId}")
    public ResponseEntity<List<GitHubRepositoryResponse>> findByGithubProfileId(@PathVariable UUID githubProfileId) {
        return ResponseEntity.ok(gitHubRepositoryService.findByGithubProfileId(githubProfileId));
    }

    @PostMapping
    public ResponseEntity<GitHubRepositoryResponse> create(@Valid @RequestBody GitHubRepositoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gitHubRepositoryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GitHubRepositoryResponse> update(@PathVariable UUID id, @Valid @RequestBody GitHubRepositoryRequest request) {
        return ResponseEntity.ok(gitHubRepositoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        gitHubRepositoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

