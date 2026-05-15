package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestion question;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeAnswer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeOutput;

    @Enumerated(EnumType.STRING)
    private CodeLanguage codeLanguage;

    private String videoUrl;
    private String audioUrl;
    private Integer retryCount;
    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("isFollowUp")
    @com.fasterxml.jackson.annotation.JsonAlias("followUp")
    private boolean isFollowUp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_answer_id")
    private SessionAnswer parentAnswer;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private AnswerEvaluation answerEvaluation;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private CodeExecutionResult codeExecutionResult;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private ArchitectureDiagram architectureDiagram;

    @OneToOne(mappedBy = "answer", cascade = CascadeType.ALL)
    private MLScenarioAnswer mlScenarioAnswer;
}
