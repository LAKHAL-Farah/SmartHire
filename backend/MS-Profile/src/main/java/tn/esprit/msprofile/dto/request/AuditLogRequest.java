package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record AuditLogRequest(
        @NotNull UUID userId,
        @NotNull OperationType operationType,
        @Size(max = 50) String entityType,
        UUID entityId,
        @Min(0) Integer tokensUsed,
        @Min(0) Integer durationMs,
        @NotNull ProcessingStatus status,
        String errorMessage,
        @Size(max = 500) String inputSummary,
        Instant createdAt
) {
}

