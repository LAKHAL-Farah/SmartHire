package tn.esprit.msprofile.dto.response;

import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID userId,
        OperationType operationType,
        String entityType,
        UUID entityId,
        Integer tokensUsed,
        Integer durationMs,
        ProcessingStatus status,
        String errorMessage,
        String inputSummary,
        Instant createdAt
) {
}

