package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ml_scenario_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MLScenarioAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private SessionAnswer answer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String modelChosen;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String featuresDescribed;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String metricsDescribed;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String deploymentStrategy;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String dataPreprocessing;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String evaluationStrategy;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedConcepts;

    private Double aiEvaluationScore;
}
