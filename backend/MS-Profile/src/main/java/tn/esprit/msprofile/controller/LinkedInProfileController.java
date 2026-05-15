package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.LinkedInProfileRequest;
import tn.esprit.msprofile.dto.response.LinkedInProfileResponse;
import tn.esprit.msprofile.service.LinkedInProfileService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/linkedin-profiles")
@RequiredArgsConstructor
public class LinkedInProfileController {

    private final LinkedInProfileService linkedInProfileService;

    @GetMapping
    public ResponseEntity<List<LinkedInProfileResponse>> findAll() {
        return ResponseEntity.ok(linkedInProfileService.findAll());
    }


    @GetMapping("/{id}")
    public ResponseEntity<LinkedInProfileResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(linkedInProfileService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<LinkedInProfileResponse> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(linkedInProfileService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<LinkedInProfileResponse> create(@Valid @RequestBody LinkedInProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkedInProfileService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LinkedInProfileResponse> update(@PathVariable UUID id, @Valid @RequestBody LinkedInProfileRequest request) {
        return ResponseEntity.ok(linkedInProfileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        linkedInProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

