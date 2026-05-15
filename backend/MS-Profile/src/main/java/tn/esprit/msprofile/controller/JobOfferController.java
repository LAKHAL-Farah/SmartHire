package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.JobOfferRequest;
import tn.esprit.msprofile.dto.response.JobOfferResponse;
import tn.esprit.msprofile.service.JobOfferService;

import java.util.List;
import java.util.UUID;

@RestController("legacyJobOfferController")
@RequestMapping("/api/v1/job-offers")
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService jobOfferService;

    @GetMapping
    public ResponseEntity<List<JobOfferResponse>> findAll() {
        return ResponseEntity.ok(jobOfferService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobOfferResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobOfferService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<JobOfferResponse>> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(jobOfferService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<JobOfferResponse> create(@Valid @RequestBody JobOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobOfferService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobOfferResponse> update(@PathVariable UUID id, @Valid @RequestBody JobOfferRequest request) {
        return ResponseEntity.ok(jobOfferService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobOfferService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

