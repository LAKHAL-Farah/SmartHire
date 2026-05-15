package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class ProjectSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_suggestion_id")
    @ToString.Exclude
    private ProjectSuggestion projectSuggestion;

    private String repoUrl;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.SubmissionStatus status = tn.esprit.msroadmap.Enums.SubmissionStatus.PENDING_REVIEW;

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    private Integer readmeScore;
    private Integer structureScore;
    private Integer testScore;
    private Integer ciScore;
    private String recommendations;
    private int retryCount = 0;

    @CreatedDate
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;
}
