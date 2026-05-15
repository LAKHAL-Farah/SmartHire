package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class PeerBenchmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long careerPathId;
    private int stepOrder;
    private double avgCompletionDays;
    private double p10CompletionDays;
    private double p90CompletionDays;
    private double fastest10Percent;
    private double slowest10Percent;
    private int totalSamples;
    private LocalDateTime computedAt;
}
