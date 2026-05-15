package tn.esprit.msassessment.dto.response;

import tn.esprit.msassessment.entity.enums.AssignmentStatus;

import java.time.Instant;

public record PendingAssignmentRow(
        String userId,
        String situation,
        String careerPath,
        AssignmentStatus status,
        Instant createdAt
) {}
