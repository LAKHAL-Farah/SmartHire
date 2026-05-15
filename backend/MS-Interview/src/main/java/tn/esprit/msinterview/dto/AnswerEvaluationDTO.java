package tn.esprit.msinterview.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerEvaluationDTO {
    private Long id;
    private Long answerId;
    private Double contentScore;
    private Double clarityScore;
    private Double technicalScore;
    private Double codeCorrectnessScore;
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
    private String aiFeedback;
    private String followUpGenerated;
    private Double avgStressScore;
    private String stressPeakLevel;
    private Integer stressReadingCount;
    private String stressTimeline;
}
