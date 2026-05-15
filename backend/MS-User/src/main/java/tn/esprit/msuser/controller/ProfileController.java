package tn.esprit.msuser.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msuser.dto.request.OnboardingCompleteRequest;
import tn.esprit.msuser.dto.request.ProfileRequest;
import tn.esprit.msuser.dto.response.ProfileResponse;
import tn.esprit.msuser.service.ProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<ProfileResponse> getProfileByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<ProfileResponse> createProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody ProfileRequest request) {
        ProfileResponse createdProfile = profileService.createProfile(userId, request);
        return new ResponseEntity<>(createdProfile, HttpStatus.CREATED);
    }

    @PutMapping("/user/{userId}")
    public ResponseEntity<ProfileResponse> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }

    @PatchMapping("/user/{userId}")
    public ResponseEntity<ProfileResponse> createOrUpdateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(profileService.createOrUpdateProfile(userId, request));
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable UUID userId) {
        profileService.deleteProfile(userId);
        return ResponseEntity.noContent().build();
    }

    /** Persist onboarding quiz + starting profile (post sign-up). Creates profile row if missing. */
    @PostMapping("/user/{userId}/onboarding-complete")
    public ResponseEntity<ProfileResponse> completeOnboarding(
            @PathVariable UUID userId,
            @Valid @RequestBody OnboardingCompleteRequest request) {
        return ResponseEntity.ok(profileService.completeOnboarding(userId, request));
    }
}
