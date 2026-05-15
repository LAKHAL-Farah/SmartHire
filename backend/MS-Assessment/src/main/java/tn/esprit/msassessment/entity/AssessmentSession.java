package tn.esprit.msassessment.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msassessment.entity.enums.SessionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One assessment attempt for a user on a {@link QuestionCategory} (user id matches MS-User).
 */
@Entity
@Table(name = "msa_assessment_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String userId;

    /**
     * Optional snapshot for admin lists (sent when the session starts). Used when MS-User HTTP lookup is unavailable.
     */
    @Column(name = "candidate_display_name", length = 256)
    private String candidateDisplayName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private QuestionCategory category;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Lifecycle: {@code IN_PROGRESS} → {@code COMPLETED} after submit. Publishing results to the candidate does not
     * change this — use {@link #resultReleasedToCandidate} (exposed as {@code isPublished} / {@code scoreReleased} in API).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    /**
     * Overall result 0–100 after submit; null while {@link SessionStatus#IN_PROGRESS}.
     */
    @Column(name = "score_percent")
    private Integer scorePercent;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Keyword used when the session was started by topic (e.g. "java"). */
    @Column(name = "topic_tag", length = 128)
    private String topicTag;

    /**
     * JSON array of question ids for this attempt (subset random pick). When null, all active questions in {@link #category} apply.
     */
    @Column(name = "selected_question_ids", columnDefinition = "TEXT")
    private String selectedQuestionIdsJson;

    /** When completed, score is hidden from the candidate until an admin sets this to true. */
    @Column(name = "result_released_to_candidate", nullable = false)
    @Builder.Default
    private boolean resultReleasedToCandidate = false;

    /** Shown to the candidate only after {@link #resultReleasedToCandidate} is true (reviewer feedback). */
    @Column(name = "admin_feedback_to_candidate", columnDefinition = "TEXT")
    private String adminFeedbackToCandidate;

    /**
     * Set when the client reports leaving the quiz surface (e.g. tab hidden). Final score is forced to 0 on submit.
     */
    @Column(name = "integrity_violation", nullable = false)
    @Builder.Default
    private boolean integrityViolation = false;

    @Column(name = "integrity_violation_at")
    private Instant integrityViolationAt;

    /**
     * Set when the candidate clicks "Back" without submitting (as opposed to tab switch/window minimize).
     * Used for frontend display only - backend treats it same as integrity violation (0% score, immediate release).
     */
    @Column(name = "forfeit", nullable = false)
    @Builder.Default
    private boolean forfeit = false;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SessionAnswer> answers = new ArrayList<>();
}
