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
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "linkedin_profile")
@Getter
@Setter
@NoArgsConstructor
public class LinkedInProfile extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(length = 512)
    private String profileUrl;

    @Lob
    private String rawContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus scrapeStatus = ProcessingStatus.PENDING;

    @Column(length = 500)
    private String scrapeErrorMessage;

    private Integer globalScore;

    @Lob
    private String sectionScoresJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 220)
    private String currentHeadline;

    @Column(length = 220)
    private String optimizedHeadline;

    @Lob
    private String currentSummary;

    @Lob
    private String optimizedSummary;

    @Lob
    private String currentSkills;

    @Lob
    private String optimizedSkills;

    @Column(length = 220)
    private String jobAlignedHeadline;

    @Lob
    private String jobAlignedSummary;

    @Lob
    private String jobAlignedSkills;

    private UUID alignedJobOfferId;

    private Instant analyzedAt;

    @PrePersist
    void prePersist() {
        if (scrapeStatus == null) {
            scrapeStatus = ProcessingStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

