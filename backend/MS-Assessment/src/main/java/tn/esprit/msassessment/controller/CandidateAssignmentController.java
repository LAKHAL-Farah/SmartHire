package tn.esprit.msassessment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msassessment.dto.request.CandidateAssignmentRegisterRequest;
import tn.esprit.msassessment.dto.response.CandidateAssignmentStatusResponse;
import tn.esprit.msassessment.service.CandidateAssignmentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/candidates")
@RequiredArgsConstructor
public class CandidateAssignmentController {

    private final CandidateAssignmentService candidateAssignmentService;

    /** Called after onboarding — creates or updates a pending plan until admin approves. */
    @PostMapping("/register")
    public ResponseEntity<CandidateAssignmentStatusResponse> register(
            @Valid @RequestBody CandidateAssignmentRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(candidateAssignmentService.register(request));
    }

    @GetMapping("/{userId}/status")
    public ResponseEntity<CandidateAssignmentStatusResponse> status(@PathVariable UUID userId) {
        return ResponseEntity.ok(candidateAssignmentService.getStatus(userId));
    }
}
