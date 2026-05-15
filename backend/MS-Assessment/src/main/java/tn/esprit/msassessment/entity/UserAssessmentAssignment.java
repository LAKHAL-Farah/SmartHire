package tn.esprit.msassessment.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msassessment.entity.enums.AssignmentStatus;

import java.time.Instant;

/**
 * Tracks a candidate's onboarding preferences and which categories an admin unlocked for MCQ attempts.
 * {@code userId} is stored as VARCHAR(36) (MS-User UUID string) for reliable MySQL mapping.
 */
@Entity
@Table(name = "user_assessment_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAssessmentAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String userId;

    @Column(length = 64)
    private String situation;

    @Column(length = 64)
    private String careerPath;

    /** User's headline from registration (e.g., "Full-stack developer with 3 years experience") */
    @Column(length = 128)
    private String headline;

    /** Custom situation if "Other" was selected */
    @Column(length = 128)
    private String customSituation;

    /** Custom career path if "Other" was selected */
    @Column(length = 128)
    private String customCareerPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.PENDING;

    /** JSON array of category ids, e.g. "[1,2]" — set when admin approves. */
    @Column(columnDefinition = "TEXT")
    private String assignedCategoryIdsJson;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant approvedAt;

    /**
     * When true, this row is hidden from GET /admin/assignments/approved (default view). Assignment stays active for the
     * candidate. Set on approve, or via admin “remove from list”.
     */
    @Column(name = "dismissed_from_admin", nullable = false)
    @Builder.Default
    private boolean dismissedFromAdmin = false;
}
