package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.HireReadinessScoreRequest;
import tn.esprit.msprofile.dto.response.HireReadinessScoreResponse;
import tn.esprit.msprofile.service.HireReadinessScoreService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hire-readiness-scores")
@RequiredArgsConstructor
public class HireReadinessScoreController {

    private final HireReadinessScoreService hireReadinessScoreService;

    @GetMapping
    public ResponseEntity<List<HireReadinessScoreResponse>> findAll() {
        return ResponseEntity.ok(hireReadinessScoreService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HireReadinessScoreResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(hireReadinessScoreService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<HireReadinessScoreResponse> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(hireReadinessScoreService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<HireReadinessScoreResponse> create(@Valid @RequestBody HireReadinessScoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hireReadinessScoreService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HireReadinessScoreResponse> update(@PathVariable UUID id, @Valid @RequestBody HireReadinessScoreRequest request) {
        return ResponseEntity.ok(hireReadinessScoreService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        hireReadinessScoreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

