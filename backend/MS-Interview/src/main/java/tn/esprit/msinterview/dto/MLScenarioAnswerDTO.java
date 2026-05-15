package tn.esprit.msinterview.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLScenarioAnswerDTO {
    private Long id;
    private Long answerId;
    private String modelChosen;
    private String featuresDescribed;
    private String metricsDescribed;
    private String deploymentStrategy;
    private String dataPreprocessing;
    private String evaluationStrategy;
    private String extractedConcepts;
    private Double aiEvaluationScore;
}
