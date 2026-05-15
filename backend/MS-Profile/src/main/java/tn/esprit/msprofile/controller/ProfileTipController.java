package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.ProfileTipRequest;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.service.ProfileTipService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile-tips")
@RequiredArgsConstructor
public class ProfileTipController {

    private final ProfileTipService profileTipService;

    @GetMapping
    public ResponseEntity<List<ProfileTipResponse>> findAll() {
        return ResponseEntity.ok(profileTipService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfileTipResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(profileTipService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ProfileTipResponse>> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileTipService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<ProfileTipResponse> create(@Valid @RequestBody ProfileTipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileTipService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfileTipResponse> update(@PathVariable UUID id, @Valid @RequestBody ProfileTipRequest request) {
        return ResponseEntity.ok(profileTipService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        profileTipService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

