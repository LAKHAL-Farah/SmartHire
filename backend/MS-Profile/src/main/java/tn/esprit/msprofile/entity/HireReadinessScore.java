package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hire_readiness_score")
@Getter
@Setter
@NoArgsConstructor
public class HireReadinessScore extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    private Integer cvScore;

    private Integer linkedinScore;

    private Integer githubScore;

    private Integer globalScore;

    @Column(nullable = false)
    private Instant computedAt;

    @PrePersist
    void prePersist() {
        if (computedAt == null) {
            computedAt = Instant.now();
        }
    }
}

