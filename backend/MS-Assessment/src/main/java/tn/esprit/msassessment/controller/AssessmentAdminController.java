package tn.esprit.msassessment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msassessment.ai.AssessmentAdviceService;
import tn.esprit.msassessment.ai.AiAdviceClient;
import tn.esprit.msassessment.dto.admin.*;
import tn.esprit.msassessment.dto.request.ApproveAssignmentRequest;
import tn.esprit.msassessment.dto.request.ReleaseSessionResultRequest;
import tn.esprit.msassessment.dto.request.AdminAssignAssessmentRequest;
import tn.esprit.msassessment.dto.request.BulkAssignAssessmentRequest;
import tn.esprit.msassessment.dto.response.*;
import tn.esprit.msassessment.config.AssessmentCategorySeedService;
import tn.esprit.msassessment.service.AssessmentAdminService;
import tn.esprit.msassessment.service.AssessmentSessionService;
import tn.esprit.msassessment.service.CandidateAssignmentService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/admin")
@RequiredArgsConstructor
public class AssessmentAdminController {

    private final AssessmentAdminService adminService;
    private final CandidateAssignmentService candidateAssignmentService;
    private final AssessmentSessionService assessmentSessionService;
    private final AssessmentCategorySeedService categorySeedService;
    private final AssessmentAdviceService adviceService;
    private final AiAdviceClient aiAdviceClient;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryAdminResponse>> listCategories() {
        return ResponseEntity.ok(adminService.listCategories());
    }

    /**
     * Inserts any default seeded categories that are missing (same logic as startup). Use this if the DB was created
     * with an older build that stopped after the first category, or to repair a partial seed without wiping data.
     */
    @PostMapping("/seed-default-bank")
    public ResponseEntity<SeedDefaultBankResponse> seedDefaultBank() {
        return ResponseEntity.ok(categorySeedService.seedAllMissingAndReport());
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<CategoryAdminResponse> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getCategory(id));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryAdminResponse> createCategory(@Valid @RequestBody CategoryAdminRequest request) {
        return new ResponseEntity<>(adminService.createCategory(request), HttpStatus.CREATED);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryAdminResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryAdminRequest request) {
        return ResponseEntity.ok(adminService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories/{categoryId}/questions")
    public ResponseEntity<List<QuestionAdminResponse>> listQuestions(@PathVariable Long categoryId) {
        return ResponseEntity.ok(adminService.listQuestions(categoryId));
    }

    @PostMapping("/categories/{categoryId}/questions")
    public ResponseEntity<QuestionAdminResponse> createQuestion(
            @PathVariable Long categoryId,
            @Valid @RequestBody QuestionAdminRequest request) {
        return new ResponseEntity<>(adminService.createQuestion(categoryId, request), HttpStatus.CREATED);
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<QuestionAdminResponse> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionAdminRequest request) {
        return ResponseEntity.ok(adminService.updateQuestion(questionId, request));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        adminService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/questions/{questionId}/choices")
    public ResponseEntity<ChoiceAdminResponse> createChoice(
            @PathVariable Long questionId,
            @Valid @RequestBody ChoiceAdminRequest request) {
        return new ResponseEntity<>(adminService.createChoice(questionId, request), HttpStatus.CREATED);
    }

    @PutMapping("/choices/{choiceId}")
    public ResponseEntity<ChoiceAdminResponse> updateChoice(
            @PathVariable Long choiceId,
            @Valid @RequestBody ChoiceAdminRequest request) {
        return ResponseEntity.ok(adminService.updateChoice(choiceId, request));
    }

    @DeleteMapping("/choices/{choiceId}")
    public ResponseEntity<Void> deleteChoice(@PathVariable Long choiceId) {
        adminService.deleteChoice(choiceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments/pending")
    public ResponseEntity<List<PendingAssignmentRow>> listPendingAssignments() {
        return ResponseEntity.ok(candidateAssignmentService.listPending());
    }

    @GetMapping("/assignments/approved")
    public ResponseEntity<List<ApprovedAssignmentRow>> listApprovedAssignments(
            @RequestParam(defaultValue = "false") boolean includeDismissed) {
        return ResponseEntity.ok(candidateAssignmentService.listApproved(includeDismissed));
    }

    @PostMapping("/assignments/{userId}/dismiss-from-list")
    public ResponseEntity<Void> dismissAssignmentFromAdminList(@PathVariable UUID userId) {
        candidateAssignmentService.dismissApprovedFromAdminList(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments/{userId}/show-in-list")
    public ResponseEntity<Void> showAssignmentInAdminList(@PathVariable UUID userId) {
        candidateAssignmentService.showApprovedInAdminList(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments/{userId}/approve")
    public ResponseEntity<CandidateAssignmentStatusResponse> approveAssignment(
            @PathVariable UUID userId,
            @Valid @RequestBody ApproveAssignmentRequest request) {
        return ResponseEntity.ok(candidateAssignmentService.approve(userId, request));
    }

    @PutMapping("/assignments/{userId}/categories")
    public ResponseEntity<CandidateAssignmentStatusResponse> updateAssignedCategories(
            @PathVariable UUID userId,
            @Valid @RequestBody ApproveAssignmentRequest request) {
        return ResponseEntity.ok(candidateAssignmentService.updateAssignedCategories(userId, request));
    }

    /** Per-question prompts, candidate picks, and correct answers for a completed session. */
    @GetMapping("/sessions/{sessionId}/review")
    public ResponseEntity<SessionResultResponse> adminSessionReview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(assessmentSessionService.getAdminSessionReview(sessionId));
    }

    /** Completed MCQ attempts waiting for admin to publish scores to the candidate. */
    @GetMapping({"/pending-release", "/sessions/pending-release"})
    public ResponseEntity<List<SessionResponse>> listSessionsPendingRelease() {
        return ResponseEntity.ok(assessmentSessionService.listPendingReleaseForAdmin());
    }

    /** All completed attempts (newest first) — use with GET .../sessions/{id}/review for answers. */
    @GetMapping("/sessions/completed")
    public ResponseEntity<List<SessionResponse>> listAllCompletedSessions() {
        return ResponseEntity.ok(assessmentSessionService.listAllCompletedForAdmin());
    }

    /** Publish score and unlock review for the candidate; sends notification. */
    @PostMapping({"/release-result/{sessionId}", "/sessions/{sessionId}/release-result"})
    public ResponseEntity<SessionResponse> releaseSessionResult(
            @PathVariable Long sessionId,
            @RequestBody(required = false) ReleaseSessionResultRequest body) {
        return ResponseEntity.ok(assessmentSessionService.releaseResultToCandidate(sessionId, body));
    }

    /**
     * All completed session scores for a single user — visible to admin regardless of publish status.
     * No publish action required; use this to monitor candidate progress.
     */
    @GetMapping("/users/{userId}/scores")
    public ResponseEntity<UserScoresSummaryResponse> getUserScores(@PathVariable UUID userId) {
        return ResponseEntity.ok(assessmentSessionService.getScoresByUser(userId));
    }

    /**
     * AI-suggested categories for a candidate based on their onboarding (situation + careerPath).
     * Admin can use this as a starting point when approving an assignment.
     */
    @GetMapping("/assignments/{userId}/suggest-categories")
    public ResponseEntity<CategorySuggestionResponse> suggestCategories(@PathVariable UUID userId) {
        var assignment = candidateAssignmentService.getAssignmentForSuggestion(userId);
        List<CategoryAdminResponse> allCategories = adminService.listCategories();
        List<String> allCodes = allCategories.stream().map(CategoryAdminResponse::code).toList();

        List<String> suggestedCodes = aiAdviceClient.suggestCategories(
                assignment.situation(), 
                assignment.careerPath(), 
                assignment.headline(),
                assignment.customSituation(),
                assignment.customCareerPath(),
                allCodes);

        List<CategoryAdminResponse> suggestedCategories = suggestedCodes.stream()
                .map(code -> allCategories.stream()
                        .filter(c -> c.code().equals(code))
                        .findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();

        return ResponseEntity.ok(new CategorySuggestionResponse(
                userId.toString(),
                assignment.situation(),
                assignment.careerPath(),
                suggestedCodes,
                suggestedCategories));
    }

    /** Delete a completed assessment session. */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId) {
        assessmentSessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Admin: Get user's assigned assessments (finished or not).
     * Returns list of categories assigned to the user with completion status.
     */
    @GetMapping("/users/{userId}/assigned-assessments")
    public ResponseEntity<List<UserAssignedAssessmentRow>> getUserAssignedAssessments(@PathVariable UUID userId) {
        return ResponseEntity.ok(candidateAssignmentService.getUserAssignedAssessments(userId));
    }

    /**
     * Admin: Assign assessments to a user.
     * Can assign to new users or re-assign to existing users (even if already completed).
     */
    @PostMapping("/assignments/assign-to-user")
    public ResponseEntity<CandidateAssignmentStatusResponse> assignAssessmentToUser(
            @Valid @RequestBody AdminAssignAssessmentRequest request) {
        return ResponseEntity.ok(candidateAssignmentService.adminAssignAssessment(
                request.userId(),
                request.categoryIds(),
                request.situation(),
                request.careerPath(),
                request.forceReassign()));
    }

    /**
     * Admin: Bulk assign assessments to multiple users.
     * Returns a summary of successes and failures.
     */
    @PostMapping("/assignments/bulk-assign")
    public ResponseEntity<AdminAssignmentResultResponse> bulkAssignAssessments(
            @Valid @RequestBody BulkAssignAssessmentRequest request) {
        return ResponseEntity.ok(candidateAssignmentService.bulkAssignAssessments(
                request.userIds(),
                request.categoryIds(),
                request.forceReassign()));
    }
}
