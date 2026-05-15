package tn.esprit.msassessment.dto.response;

import java.time.Instant;
import java.util.List;

/** Admin list row: candidate with approved category assignments. */
public record ApprovedAssignmentRow(
        String userId,
        String situation,
        String careerPath,
        Instant approvedAt,
        List<CandidateAssignmentStatusResponse.AssignedCategory> categories,
        /** When true, hidden from the default admin roster until restored. */
        boolean dismissedFromAdmin
) {}
