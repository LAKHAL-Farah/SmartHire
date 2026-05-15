package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerBenchmarkDto {
    private Long id;
    private Long careerPathId;
    private int stepOrder;
    private Double avgCompletionDays;
    private Double p10CompletionDays;
    private Double p90CompletionDays;
    private int totalSamples;
    private LocalDateTime computedAt;
}
