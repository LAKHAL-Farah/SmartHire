package tn.esprit.msassessment.dto.response;

import java.util.List;

public record AdminAssignmentResultResponse(
        int successCount,
        int failureCount,
        List<String> errors,
        String message
) {}
