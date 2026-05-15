package tn.esprit.msassessment.dto.response;

import tn.esprit.msassessment.entity.enums.AssignmentStatus;

import java.time.Instant;
import java.util.List;

public record CandidateAssignmentStatusResponse(
        String userId,
        AssignmentStatus status,
        String situation,
        String careerPath,
        Instant createdAt,
        Instant approvedAt,
        List<AssignedCategory> assignedCategories
) {

    /** One assigned category for API payloads (same JSON shape as before: id, code, title). */
    public record AssignedCategory(Long id, String code, String title) {}
}
