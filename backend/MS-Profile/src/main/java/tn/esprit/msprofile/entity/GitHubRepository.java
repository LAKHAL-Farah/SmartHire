package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "github_repository")
@Getter
@Setter
@NoArgsConstructor
public class GitHubRepository extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_profile_id", nullable = false)
    private GitHubProfile githubProfile;

    @Column(nullable = false, length = 255)
    private String repoName;

    @Column(nullable = false, length = 512)
    private String repoUrl;

    @Column(length = 512)
    private String description;

    @Column(length = 60)
    private String language;

    @Column(nullable = false)
    private Integer stars = 0;

    @Column(nullable = false)
    private Integer forksCount = 0;

    @Column(nullable = false)
    private Boolean isForked = Boolean.FALSE;

    @Column(nullable = false)
    private Boolean isArchived = Boolean.FALSE;

    private Instant pushedAt;

    private Integer readmeScore;

    private Boolean hasCiCd;

    private Boolean hasTests;

    private Integer codeStructureScore;

    @Lob
    private String auditFeedback;

    @Lob
    private String fixSuggestions;

    @Lob
    private String detectedIssues;

    private Instant updatedAt;

    private Integer overallScore;

    @PrePersist
    void prePersist() {
        if (stars == null) {
            stars = 0;
        }
        if (forksCount == null) {
            forksCount = 0;
        }
        if (isForked == null) {
            isForked = Boolean.FALSE;
        }
        if (isArchived == null) {
            isArchived = Boolean.FALSE;
        }
    }
}

