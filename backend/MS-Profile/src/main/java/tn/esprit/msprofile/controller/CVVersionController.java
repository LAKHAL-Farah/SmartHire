package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.CVVersionRequest;
import tn.esprit.msprofile.dto.response.CVVersionResponse;
import tn.esprit.msprofile.service.CVVersionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cv-versions")
@RequiredArgsConstructor
public class CVVersionController {

    private final CVVersionService cvVersionService;

    @GetMapping
    public ResponseEntity<List<CVVersionResponse>> findAll() {
        return ResponseEntity.ok(cvVersionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CVVersionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(cvVersionService.findById(id));
    }

    @GetMapping("/cv/{cvId}")
    public ResponseEntity<List<CVVersionResponse>> findByCvId(@PathVariable UUID cvId) {
        return ResponseEntity.ok(cvVersionService.findByCvId(cvId));
    }

    @GetMapping("/job-offer/{jobOfferId}")
    public ResponseEntity<List<CVVersionResponse>> findByJobOfferId(@PathVariable UUID jobOfferId) {
        return ResponseEntity.ok(cvVersionService.findByJobOfferId(jobOfferId));
    }

    @PostMapping
    public ResponseEntity<CVVersionResponse> create(@Valid @RequestBody CVVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cvVersionService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CVVersionResponse> update(@PathVariable UUID id, @Valid @RequestBody CVVersionRequest request) {
        return ResponseEntity.ok(cvVersionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cvVersionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

