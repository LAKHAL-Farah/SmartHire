package tn.esprit.msassessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * The candidate's pick for one question inside an {@link AssessmentSession}.
 */
@Entity
@Table(
        name = "session_answer",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_question", columnNames = {"session_id", "question_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AssessmentSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_choice_id", nullable = false)
    private AnswerChoice selectedChoice;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;
}
