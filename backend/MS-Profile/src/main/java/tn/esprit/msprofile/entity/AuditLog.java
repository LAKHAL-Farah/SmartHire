package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OperationType operationType;

    @Column(length = 50)
    private String entityType;

    private UUID entityId;

    private Integer tokensUsed;

    private Integer durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus status;

    @Column(length = 500)
    private String errorMessage;

    @Column(length = 500)
    private String inputSummary;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

