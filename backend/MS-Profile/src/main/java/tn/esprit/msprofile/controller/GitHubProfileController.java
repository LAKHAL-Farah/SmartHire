package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.GitHubProfileRequest;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.service.GitHubProfileService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github-profiles")
@RequiredArgsConstructor
public class GitHubProfileController {

    private final GitHubProfileService gitHubProfileService;

    @GetMapping
    public ResponseEntity<List<GitHubProfileResponse>> findAll() {
        return ResponseEntity.ok(gitHubProfileService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GitHubProfileResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(gitHubProfileService.findById(id));
    }


    @GetMapping("/user/{userId}")
    public ResponseEntity<GitHubProfileResponse> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(gitHubProfileService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<GitHubProfileResponse> create(@Valid @RequestBody GitHubProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gitHubProfileService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GitHubProfileResponse> update(@PathVariable UUID id, @Valid @RequestBody GitHubProfileRequest request) {
        return ResponseEntity.ok(gitHubProfileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        gitHubProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

