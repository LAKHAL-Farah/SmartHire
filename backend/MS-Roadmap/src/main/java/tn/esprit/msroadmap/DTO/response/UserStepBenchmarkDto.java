package tn.esprit.msroadmap.DTO.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStepBenchmarkDto {
    private Long id;
    private Long userId;
    private Long stepId;
    private int userDays;
    private Double percentileRank;
    private PeerBenchmarkDto peerBenchmark;
}
