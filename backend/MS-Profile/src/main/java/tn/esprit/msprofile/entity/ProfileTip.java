package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.entity.enums.TipPriority;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_tip")
@Getter
@Setter
@NoArgsConstructor
public class ProfileTip extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProfileType profileType;

    private UUID sourceEntityId;

    @Lob
    @Column(nullable = false)
    private String tipText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TipPriority priority;

    @Column(nullable = false)
    private Boolean isResolved = Boolean.FALSE;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isResolved == null) {
            isResolved = Boolean.FALSE;
        }
    }
}

