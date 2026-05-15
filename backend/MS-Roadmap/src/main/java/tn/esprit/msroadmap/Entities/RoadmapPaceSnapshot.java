package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class RoadmapPaceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    @ToString.Exclude
    private Roadmap roadmap;

    private LocalDate snapshotDate;
    private int plannedSteps;
    private int completedSteps;
    private int timedeltaDays;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.PaceStatus paceStatus;

    @Column(columnDefinition = "TEXT")
    private String catchUpPlanNote;
}
