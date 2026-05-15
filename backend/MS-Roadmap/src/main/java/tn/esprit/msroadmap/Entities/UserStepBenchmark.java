package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class UserStepBenchmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_step_id")
    @ToString.Exclude
    private RoadmapStep roadmapStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "peer_benchmark_id")
    @ToString.Exclude
    private PeerBenchmark peerBenchmark;

    private int userDays;
    private Double percentileRank;
}
