package tn.esprit.msassessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.dto.request.ApproveAssignmentRequest;
import tn.esprit.msassessment.dto.request.CandidateAssignmentRegisterRequest;
import tn.esprit.msassessment.dto.response.ApprovedAssignmentRow;
import tn.esprit.msassessment.dto.response.CandidateAssignmentStatusResponse;
import tn.esprit.msassessment.dto.response.CandidateAssignmentStatusResponse.AssignedCategory;
import tn.esprit.msassessment.dto.response.PendingAssignmentRow;
import tn.esprit.msassessment.dto.response.AdminAssignmentResultResponse;
import tn.esprit.msassessment.dto.response.UserAssignedAssessmentRow;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.entity.UserAssessmentAssignment;
import tn.esprit.msassessment.entity.enums.AssignmentStatus;
import tn.esprit.msassessment.exception.BusinessException;
import tn.esprit.msassessment.exception.ResourceNotFoundException;
import tn.esprit.msassessment.repository.AssessmentSessionRepository;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;
import tn.esprit.msassessment.repository.UserAssessmentAssignmentRepository;
import tn.esprit.msassessment.entity.enums.SessionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CandidateAssignmentService {

    private final UserAssessmentAssignmentRepository assignmentRepository;
    private final QuestionCategoryRepository categoryRepository;
    private final AssessmentSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public CandidateAssignmentStatusResponse register(CandidateAssignmentRegisterRequest request) {
        String uid = request.userId().toString();
        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(uid)
                .orElse(null);

        if (row != null && row.getStatus() == AssignmentStatus.APPROVED) {
            return toResponse(row);
        }

        Instant now = Instant.now();
        if (row == null) {
            row = UserAssessmentAssignment.builder()
                    .userId(uid)
                    .situation(trimOrNull(request.situation()))
                    .careerPath(trimOrNull(request.careerPath()))
                    .headline(trimOrNull(request.headline()))
                    .customSituation(trimOrNull(request.customSituation()))
                    .customCareerPath(trimOrNull(request.customCareerPath()))
                    .status(AssignmentStatus.PENDING)
                    .createdAt(now)
                    .build();
        } else {
            row.setSituation(trimOrNull(request.situation()));
            row.setCareerPath(trimOrNull(request.careerPath()));
            row.setHeadline(trimOrNull(request.headline()));
            row.setCustomSituation(trimOrNull(request.customSituation()));
            row.setCustomCareerPath(trimOrNull(request.customCareerPath()));
            if (row.getStatus() == AssignmentStatus.PENDING) {
                row.setCreatedAt(row.getCreatedAt() != null ? row.getCreatedAt() : now);
            }
        }
        return toResponse(assignmentRepository.save(row));
    }

    @Transactional(readOnly = true)
    public CandidateAssignmentStatusResponse getStatus(UUID userId) {
        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(userId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("No assessment plan for user: " + userId));
        return toResponse(row);
    }

    /** Used when user has not registered yet — returns synthetic pending (caller may show onboarding). */
    @Transactional(readOnly = true)
    public boolean existsForUser(UUID userId) {
        return assignmentRepository.findByUserId(userId.toString()).isPresent();
    }

    /**
     * Returns a lightweight view of the assignment for AI category suggestion.
     * If no assignment exists, returns an empty context (suggestion still works with empty strings).
     */
    @Transactional(readOnly = true)
    public AssignmentSuggestionContext getAssignmentForSuggestion(UUID userId) {
        return assignmentRepository.findByUserId(userId.toString())
                .map(a -> new AssignmentSuggestionContext(
                        a.getSituation(), 
                        a.getCareerPath(), 
                        a.getHeadline(),
                        a.getCustomSituation(),
                        a.getCustomCareerPath()))
                .orElse(new AssignmentSuggestionContext(null, null, null, null, null));
    }

    public record AssignmentSuggestionContext(String situation, String careerPath, String headline, 
                                            String customSituation, String customCareerPath) {}

    @Transactional(readOnly = true)
    public List<PendingAssignmentRow> listPending() {
        return assignmentRepository.findByStatusOrderByCreatedAtAsc(AssignmentStatus.PENDING).stream()
                .map(a -> new PendingAssignmentRow(
                        a.getUserId(),
                        a.getSituation(),
                        a.getCareerPath(),
                        a.getStatus(),
                        a.getCreatedAt()))
                .toList();
    }

    /**
     * Approved assignments for the admin roster. When {@code includeDismissed} is false (default), rows marked
     * dismissed are omitted so finished assignments do not clutter the UI.
     */
    @Transactional(readOnly = true)
    public List<ApprovedAssignmentRow> listApproved(boolean includeDismissed) {
        List<UserAssessmentAssignment> rows =
                includeDismissed
                        ? assignmentRepository.findByStatusOrderByApprovedAtDesc(AssignmentStatus.APPROVED)
                        : assignmentRepository.findByStatusAndDismissedFromAdminOrderByApprovedAtDesc(
                                AssignmentStatus.APPROVED, false);
        return rows.stream()
                .map(a -> {
                    List<AssignedCategory> views = new ArrayList<>();
                    for (Long id : parseCategoryIds(a.getAssignedCategoryIdsJson())) {
                        categoryRepository
                                .findById(id)
                                .ifPresent(c -> views.add(
                                        new AssignedCategory(c.getId(), c.getCode(), c.getTitle())));
                    }
                    return new ApprovedAssignmentRow(
                            a.getUserId(),
                            a.getSituation(),
                            a.getCareerPath(),
                            a.getApprovedAt(),
                            views,
                            a.isDismissedFromAdmin());
                })
                .toList();
    }

    public CandidateAssignmentStatusResponse approve(UUID userId, ApproveAssignmentRequest request) {
        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(userId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found for user: " + userId));

        if (row.getStatus() == AssignmentStatus.APPROVED) {
            throw new BusinessException("This candidate is already approved.");
        }

        row.setAssignedCategoryIdsJson(buildAssignedCategoryIdsJson(request));
        row.setStatus(AssignmentStatus.APPROVED);
        row.setApprovedAt(Instant.now());
        row.setDismissedFromAdmin(true);
        return toResponse(assignmentRepository.save(row));
    }

    /** Hide an approved candidate from the default admin list (assignment remains active for the candidate). */
    public void dismissApprovedFromAdminList(UUID userId) {
        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(userId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found for user: " + userId));
        if (row.getStatus() != AssignmentStatus.APPROVED) {
            throw new BusinessException("Only approved assignments can be removed from this list.");
        }
        row.setDismissedFromAdmin(true);
        assignmentRepository.save(row);
    }

    /** Show an approved candidate again in the admin list (e.g. to edit categories). */
    public void showApprovedInAdminList(UUID userId) {
        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(userId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found for user: " + userId));
        if (row.getStatus() != AssignmentStatus.APPROVED) {
            throw new BusinessException("Only approved assignments can be shown in this list.");
        }
        row.setDismissedFromAdmin(false);
        assignmentRepository.save(row);
    }

    /** Change category list for an already-approved candidate (admin correction). */
    public CandidateAssignmentStatusResponse updateAssignedCategories(UUID userId, ApproveAssignmentRequest request) {
        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(userId.toString())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found for user: " + userId));

        if (row.getStatus() != AssignmentStatus.APPROVED) {
            throw new BusinessException(
                    "This assignment is not approved yet. Approve the candidate from the pending list first.");
        }

        row.setAssignedCategoryIdsJson(buildAssignedCategoryIdsJson(request));
        return toResponse(assignmentRepository.save(row));
    }

    private String buildAssignedCategoryIdsJson(ApproveAssignmentRequest request) {
        List<Long> ids = new ArrayList<>(request.categoryIds().stream().distinct().toList());
        for (Long id : ids) {
            if (!categoryRepository.existsById(id)) {
                throw new BusinessException("Unknown category id: " + id);
            }
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize category ids", e);
        }
    }

    /**
     * Validates that the user may start a session on this category.
     * If no assignment row exists, attempts are allowed (legacy / open mode).
     * Once approved, only categories listed in the admin assignment may be started.
     */
    @Transactional(readOnly = true)
    public void assertMayStartSession(UUID userId, Long categoryId) {
        UserAssessmentAssignment row = assignmentRepository.findByUserId(userId.toString()).orElse(null);
        if (row == null) {
            return;
        }
        if (row.getStatus() == AssignmentStatus.PENDING) {
            throw new BusinessException(
                    "Your skill assessments are not unlocked yet. Wait until an admin assigns categories for you.");
        }
        if (row.getStatus() == AssignmentStatus.APPROVED) {
            List<Long> allowed = parseCategoryIds(row.getAssignedCategoryIdsJson());
            Set<Long> allowedSet = new HashSet<>(allowed);
            if (allowedSet.isEmpty()) {
                throw new BusinessException(
                        "No assessment categories are assigned to you yet. Contact your administrator.");
            }
            if (!allowedSet.contains(categoryId)) {
                throw new BusinessException(
                        "This category is not part of your assigned assessments. Open Skill assessments and use only the categories your administrator selected.");
            }
            if (!categoryRepository.existsById(categoryId)) {
                throw new BusinessException("Unknown assessment category.");
            }
            // Prevent retakes: check if user already has any session for this category (completed, integrity violation, or forfeit)
            if (sessionRepository.existsByUserIdAndCategory_Id(userId.toString(), categoryId)) {
                throw new BusinessException("You have already attempted this assessment. Each category allows only one attempt.");
            }
        }
    }

    /**
     * Topic-based sessions: blocked while plan is pending; blocked for approved candidates (they must use assigned
     * categories from the hub).
     */
    public void assertMayStartTopicSession(UUID userId) {
        UserAssessmentAssignment row = assignmentRepository.findByUserId(userId.toString()).orElse(null);
        if (row == null) {
            return;
        }
        if (row.getStatus() == AssignmentStatus.PENDING) {
            throw new BusinessException(
                    "Your skill assessments are not unlocked yet. Wait until an admin assigns categories for you.");
        }
        if (row.getStatus() == AssignmentStatus.APPROVED) {
            throw new BusinessException(
                    "Use the skill assessment hub and start only the categories your administrator assigned to you.");
        }
    }

    private List<Long> parseCategoryIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private CandidateAssignmentStatusResponse toResponse(UserAssessmentAssignment a) {
        List<AssignedCategory> views = new ArrayList<>();
        if (a.getStatus() == AssignmentStatus.APPROVED) {
            for (Long id : parseCategoryIds(a.getAssignedCategoryIdsJson())) {
                categoryRepository
                        .findById(id)
                        .ifPresent(c -> views.add(
                                new AssignedCategory(c.getId(), c.getCode(), c.getTitle())));
            }
        }
        return new CandidateAssignmentStatusResponse(
                a.getUserId(),
                a.getStatus(),
                a.getSituation(),
                a.getCareerPath(),
                a.getCreatedAt(),
                a.getApprovedAt(),
                views);
    }

    /**
     * Admin: Assign assessments to a user (even if already completed).
     * Creates or updates the assignment with the specified categories.
     * Merges new categories with existing ones (does not replace).
     */
    public CandidateAssignmentStatusResponse adminAssignAssessment(
            UUID userId, 
            List<Long> categoryIds, 
            String situation, 
            String careerPath,
            boolean forceReassign) {
        
        // Validate categories exist
        for (Long id : categoryIds) {
            if (!categoryRepository.existsById(id)) {
                throw new BusinessException("Unknown category id: " + id);
            }
        }

        UserAssessmentAssignment row = assignmentRepository
                .findByUserId(userId.toString())
                .orElse(null);

        Instant now = Instant.now();
        if (row == null) {
            // Create new assignment
            row = UserAssessmentAssignment.builder()
                    .userId(userId.toString())
                    .situation(trimOrNull(situation))
                    .careerPath(trimOrNull(careerPath))
                    .status(AssignmentStatus.APPROVED)
                    .createdAt(now)
                    .approvedAt(now)
                    .dismissedFromAdmin(false)
                    .build();
        } else {
            // Update existing assignment
            if (situation != null) {
                row.setSituation(trimOrNull(situation));
            }
            if (careerPath != null) {
                row.setCareerPath(trimOrNull(careerPath));
            }
            
            // If not already approved, approve it
            if (row.getStatus() != AssignmentStatus.APPROVED) {
                row.setStatus(AssignmentStatus.APPROVED);
                row.setApprovedAt(now);
            }
            row.setDismissedFromAdmin(false);
        }

        // Merge new categories with existing ones (do not replace)
        List<Long> existingCategoryIds = parseCategoryIds(row.getAssignedCategoryIdsJson());
        Set<Long> mergedCategories = new HashSet<>(existingCategoryIds);
        mergedCategories.addAll(categoryIds);
        
        // Set the merged categories
        List<Long> mergedList = new ArrayList<>(mergedCategories);
        row.setAssignedCategoryIdsJson(buildAssignedCategoryIdsJson(
                new ApproveAssignmentRequest(mergedList)));

        return toResponse(assignmentRepository.save(row));
    }

    /**
     * Admin: Bulk assign assessments to multiple users.
     * Returns a summary of successes and failures.
     */
    public AdminAssignmentResultResponse bulkAssignAssessments(
            List<UUID> userIds,
            List<Long> categoryIds,
            boolean forceReassign) {
        
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (UUID userId : userIds) {
            try {
                adminAssignAssessment(userId, categoryIds, null, null, forceReassign);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                errors.add("User " + userId + ": " + e.getMessage());
            }
        }

        String message = String.format(
                "Bulk assignment completed: %d successful, %d failed",
                successCount, failureCount);

        return new AdminAssignmentResultResponse(successCount, failureCount, errors, message);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Get user's assigned assessments with completion status.
     * Returns all categories assigned to the user, showing which ones are completed.
     */
    public List<UserAssignedAssessmentRow> getUserAssignedAssessments(UUID userId) {
        UserAssessmentAssignment assignment = assignmentRepository
                .findByUserId(userId.toString())
                .orElse(null);

        if (assignment == null || assignment.getStatus() != AssignmentStatus.APPROVED) {
            return List.of();
        }

        List<Long> assignedCategoryIds = parseCategoryIds(assignment.getAssignedCategoryIdsJson());
        List<UserAssignedAssessmentRow> result = new ArrayList<>();

        for (Long categoryId : assignedCategoryIds) {
            QuestionCategory category = categoryRepository.findById(categoryId).orElse(null);
            if (category == null) {
                continue;
            }

            // Check if user has completed this category (any session exists for this user+category)
            boolean completed = sessionRepository.existsByUserIdAndCategory_Id(
                    userId.toString(),
                    categoryId
            );

            result.add(new UserAssignedAssessmentRow(
                    categoryId,
                    category.getCode(),
                    category.getTitle(),
                    "ASSIGNED",
                    completed
            ));
        }

        return result;
    }
}
