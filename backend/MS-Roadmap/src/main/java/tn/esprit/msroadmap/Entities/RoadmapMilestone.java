package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class RoadmapMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    @ToString.Exclude
    private Roadmap roadmap;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Min(1)
    private int stepThreshold;
    private LocalDateTime reachedAt;
    private boolean certificateIssued = false;
}
