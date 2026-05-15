package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class RoadmapStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    @ToString.Exclude
    private Roadmap roadmap;

    private Integer stepOrder;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String objective;

    private int estimatedDays;
    private Integer actualDays;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.StepStatus status = tn.esprit.msroadmap.Enums.StepStatus.LOCKED;

    private LocalDateTime unlockedAt;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StepResource> resources;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectSuggestion> projectSuggestions;
}