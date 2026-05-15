package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "candidate_cv")
@Getter
@Setter
@NoArgsConstructor
@DynamicUpdate
public class CandidateCV extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 512)
    private String originalFileUrl;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileFormat fileFormat;

    @Lob
    private String parsedContent;

    @Lob
    @Column(name = "ats_analysis", columnDefinition = "LONGTEXT")
    private String atsAnalysis;

    @Lob
    @Column(name = "completeness_analysis", columnDefinition = "LONGTEXT")
    private String completenessAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus parseStatus = ProcessingStatus.PENDING;

    @Column(length = 500)
    private String parseErrorMessage;

    private Integer atsScore;

    @Column(nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(nullable = false)
    private Instant uploadedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cv", cascade = ALL, orphanRemoval = true, fetch = LAZY)
    private List<CVVersion> versions = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = uploadedAt;
        }
        if (parseStatus == null) {
            parseStatus = ProcessingStatus.PENDING;
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

