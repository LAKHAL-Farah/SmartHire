package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "session_question_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionQuestionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestion question;

    @Column(nullable = false)
    private Integer questionOrder;

    private Long nextQuestionId;
    private Integer timeAllottedSeconds;

    @com.fasterxml.jackson.annotation.JsonProperty("wasSkipped")
    @com.fasterxml.jackson.annotation.JsonAlias("skipped")
    private boolean wasSkipped;
}
