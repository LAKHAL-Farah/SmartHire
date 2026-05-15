package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msprofile.dto.request.AuditLogRequest;
import tn.esprit.msprofile.dto.response.AuditLogResponse;
import tn.esprit.msprofile.service.AuditLogService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> findAll() {
        return ResponseEntity.ok(auditLogService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AuditLogResponse>> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(auditLogService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<AuditLogResponse> create(@Valid @RequestBody AuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditLogService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditLogResponse> update(@PathVariable UUID id, @Valid @RequestBody AuditLogRequest request) {
        return ResponseEntity.ok(auditLogService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        auditLogService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

