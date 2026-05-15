package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cv_version")
@Getter
@Setter
@NoArgsConstructor
public class CVVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cv_id", nullable = false)
    private CandidateCV cv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_offer_id")
    private JobOffer jobOffer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CVVersionType versionType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String tailoredContent;

    private Integer atsScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal keywordMatchRate;

    @Lob
    @Column(name = "ats_analysis", columnDefinition = "LONGTEXT")
    private String atsAnalysis;

    @Lob
    @Column(name = "completeness_analysis", columnDefinition = "LONGTEXT")
    private String completenessAnalysis;

    @Lob
    private String diffContent;

    @Column(nullable = false)
    private Boolean generatedByAI = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(length = 512)
    private String exportedFileUrl;

    @Column(nullable = false)
    private Instant generatedAt;

    @PrePersist
    void prePersist() {
        if (generatedByAI == null) {
            generatedByAI = Boolean.FALSE;
        }
        if (processingStatus == null) {
            processingStatus = ProcessingStatus.PENDING;
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}

