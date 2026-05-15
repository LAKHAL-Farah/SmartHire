package tn.esprit.msprofile.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.AuditLogRequest;
import tn.esprit.msprofile.dto.response.AuditLogResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.repository.AuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService extends AbstractCrudService<AuditLog, AuditLogResponse> {

    private final AuditLogRepository auditLogRepository;

    @Override
    protected JpaRepository<AuditLog, UUID> repository() {
        return auditLogRepository;
    }

    @Override
    protected AuditLogResponse toResponse(AuditLog entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getOperationType(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getTokensUsed(),
                entity.getDurationMs(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getInputSummary(),
                entity.getCreatedAt()
        );
    }

    @Override
    protected String resourceName() {
        return "AuditLog";
    }

    @Transactional
    public AuditLog logOperation(
            UUID userId,
            OperationType type,
            String entityType,
            UUID entityId,
            ProcessingStatus status,
            Integer tokensUsed,
            Integer durationMs,
            String errorMessage
    ) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setOperationType(type);
        log.setEntityType(trimToNull(entityType));
        log.setEntityId(entityId);
        log.setStatus(status);
        log.setTokensUsed(tokensUsed);
        log.setDurationMs(durationMs);
        log.setErrorMessage(trimToNull(errorMessage));
        log.setCreatedAt(Instant.now());
        return auditLogRepository.save(log);
    }

    @Transactional
    public void updateLog(UUID logId, ProcessingStatus status, Integer tokensUsed, Integer durationMs, String errorMessage) {
        AuditLog existing = auditLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("AuditLog not found with id=" + logId));
        existing.setStatus(status);
        existing.setTokensUsed(tokensUsed);
        existing.setDurationMs(durationMs);
        existing.setErrorMessage(trimToNull(errorMessage));
        auditLogRepository.save(existing);
    }

    public List<AuditLogResponse> getLogsForUser(UUID userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AuditLogResponse> getLogsForEntity(String entityType, UUID entityId) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AuditLogResponse> getLogsByOperation(UUID userId, OperationType type) {
        return auditLogRepository.findByUserIdAndOperationType(userId, type).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AuditLogResponse> findByUserId(UUID userId) {
        return getLogsForUser(userId);
    }

    @Transactional
    public AuditLogResponse create(AuditLogRequest request) {
        AuditLog entity = new AuditLog();
        apply(entity, request);
        return toResponse(auditLogRepository.save(entity));
    }

    @Transactional
    public AuditLogResponse update(UUID id, AuditLogRequest request) {
        AuditLog entity = requireEntity(id);
        apply(entity, request);
        return toResponse(auditLogRepository.save(entity));
    }

    private void apply(AuditLog entity, AuditLogRequest request) {
        entity.setUserId(request.userId());
        entity.setOperationType(request.operationType());
        entity.setEntityType(trimToNull(request.entityType()));
        entity.setEntityId(request.entityId());
        entity.setTokensUsed(request.tokensUsed());
        entity.setDurationMs(request.durationMs());
        entity.setStatus(request.status());
        entity.setErrorMessage(trimToNull(request.errorMessage()));
        entity.setInputSummary(trimToNull(request.inputSummary()));
        entity.setCreatedAt(request.createdAt());
    }
}

