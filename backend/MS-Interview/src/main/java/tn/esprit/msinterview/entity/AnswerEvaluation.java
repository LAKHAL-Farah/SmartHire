package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "answer_evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private SessionAnswer answer;

    private Double contentScore;
    private Double clarityScore;
    private Double technicalScore;
    private Double codeCorrectnessScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeComplexityNote;

    private Double confidenceScore;
    private Double toneScore;
    private Double emotionScore;
    private Double speechRate;
    private Integer hesitationCount;
    private Double postureScore;
    private Double eyeContactScore;
    private Double expressionScore;
    private Double overallScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String followUpGenerated;

    @Column
    private Double avgStressScore;

    @Column
    private String stressPeakLevel;

    @Column
    private Integer stressReadingCount;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stressTimeline;
}
