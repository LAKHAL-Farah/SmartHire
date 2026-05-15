package tn.esprit.msjob.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_job_applications_job_user", columnNames = {"job_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_job_applications_job_id", columnList = "job_id"),
                @Index(name = "idx_job_applications_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Placeholder until we integrate with User MS.
     */
    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    /**
     * Link/path to the resume stored on disk.
     * Example: /uploads/resumes/{uuid}.pdf
     */
    @NotNull
    @Column(name = "resume_url", nullable = false, length = 1024)
    private String resumeUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @NotNull
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;
}

