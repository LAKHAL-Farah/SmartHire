package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.CandidateCVRequest;
import tn.esprit.msprofile.dto.response.CandidateCVResponse;
import tn.esprit.msprofile.service.CandidateCVService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidate-cvs")
@RequiredArgsConstructor
public class CandidateCVController {

    private final CandidateCVService candidateCVService;

    @GetMapping
    public ResponseEntity<List<CandidateCVResponse>> findAll() {
        return ResponseEntity.ok(candidateCVService.findAll());
    }


    @GetMapping("/{id}")
    public ResponseEntity<CandidateCVResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(candidateCVService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CandidateCVResponse>> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(candidateCVService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<CandidateCVResponse> create(@Valid @RequestBody CandidateCVRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(candidateCVService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CandidateCVResponse> update(@PathVariable UUID id, @Valid @RequestBody CandidateCVRequest request) {
        return ResponseEntity.ok(candidateCVService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        candidateCVService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

