package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class ProjectSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id")
    @ToString.Exclude
    private RoadmapStep step;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.DifficultyLevel difficulty;

    @ElementCollection
    private List<String> githubTopics;

    @ElementCollection
    private List<String> techStack;

    private int estimatedDays;
    private String alignedCareerPath;
    private LocalDateTime createdAt;
}
