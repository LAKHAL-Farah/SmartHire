package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "github_profile")
@Getter
@Setter
@NoArgsConstructor
public class GitHubProfile extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 100, unique = true)
    private String githubUsername;

    @Column(length = 512)
    private String profileUrl;

    private Integer overallScore;

    private Integer repoCount;

    @Lob
    private String topLanguages;

    private Integer profileReadmeScore;

    @Lob
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus auditStatus = ProcessingStatus.PENDING;

    @Column(length = 500)
    private String auditErrorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant analyzedAt;

    @OneToMany(mappedBy = "githubProfile", cascade = ALL, orphanRemoval = true, fetch = LAZY)
    private List<GitHubRepository> repositories = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (auditStatus == null) {
            auditStatus = ProcessingStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

