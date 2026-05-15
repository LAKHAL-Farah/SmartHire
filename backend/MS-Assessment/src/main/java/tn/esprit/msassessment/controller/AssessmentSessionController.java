package tn.esprit.msassessment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msassessment.dto.request.ForfeitSessionRequest;
import tn.esprit.msassessment.dto.request.IntegrityViolationRequest;
import tn.esprit.msassessment.dto.request.StartSessionByTopicRequest;
import tn.esprit.msassessment.dto.request.StartSessionRequest;
import tn.esprit.msassessment.dto.request.SubmitAnswersRequest;
import tn.esprit.msassessment.dto.response.QuestionPaperResponse;
import tn.esprit.msassessment.dto.response.SessionResponse;
import tn.esprit.msassessment.dto.response.SessionResultResponse;
import tn.esprit.msassessment.service.AssessmentSessionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/sessions")
@RequiredArgsConstructor
public class AssessmentSessionController {

    private final AssessmentSessionService sessionService;

    /** Start an attempt for a user on a question category. */
    @PostMapping
    public ResponseEntity<SessionResponse> start(@Valid @RequestBody StartSessionRequest request) {
        return new ResponseEntity<>(sessionService.start(request), HttpStatus.CREATED);
    }

    /** Start an attempt with a random subset of questions matching a topic keyword (e.g. java). */
    @PostMapping("/by-topic")
    public ResponseEntity<SessionResponse> startByTopic(@Valid @RequestBody StartSessionByTopicRequest request) {
        return new ResponseEntity<>(sessionService.startByTopic(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SessionResponse>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(sessionService.listForUser(userId));
    }

    /** Questions + choices (correct flags hidden). */
    @GetMapping("/{id}/paper")
    public ResponseEntity<QuestionPaperResponse> paper(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getPaper(id));
    }

    /**
     * Report that the candidate left the quiz UI (e.g. switched tab). The attempt is flagged; submit will score 0.
     */
    @PostMapping("/{id}/integrity-violation")
    public ResponseEntity<SessionResponse> reportIntegrityViolation(
            @PathVariable Long id,
            @RequestBody(required = false) IntegrityViolationRequest request) {
        return ResponseEntity.ok(sessionService.reportIntegrityViolation(id, request));
    }

    /**
     * Leave without submitting (e.g. in-app back). Score 0, flagged, result released immediately — no admin step.
     */
    @PostMapping("/{id}/forfeit")
    public ResponseEntity<SessionResponse> forfeit(@PathVariable Long id, @Valid @RequestBody ForfeitSessionRequest request) {
        return ResponseEntity.ok(sessionService.forfeit(id, request));
    }

    /** Submit all answers; session is scored and closed. */
    @PostMapping("/{id}/submit")
    public ResponseEntity<SessionResponse> submit(
            @PathVariable Long id,
            @Valid @RequestBody SubmitAnswersRequest request) {
        return ResponseEntity.ok(sessionService.submit(id, request));
    }

    /**
     * Per-question breakdown after completion. Requires admin to have published results.
     * Pass {@code userId} (same as session owner) so the server can verify the caller.
     */
    @GetMapping("/{id}/review")
    public ResponseEntity<SessionResultResponse> review(
            @PathVariable Long id, @RequestParam(required = false) UUID userId) {
        return ResponseEntity.ok(sessionService.getReview(id, userId));
    }
}
