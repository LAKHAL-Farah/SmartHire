package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString @EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class Roadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    private Long userId;
    private Long careerPathId;
    private String title;
    private String difficulty;
    private Integer estimatedWeeks;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.RoadmapStatus status = tn.esprit.msroadmap.Enums.RoadmapStatus.GENERATING;

    private int totalSteps = 0;
    private int completedSteps = 0;
    private boolean isAiGenerated = false;
    private int streakDays = 0;
    private int longestStreak = 0;
    private LocalDate lastActivityDate;
    @Column(unique = true)
    private String shareToken;
    private boolean isPublic = false;
    private LocalDateTime generatedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "roadmap", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoadmapStep> steps;

    @OneToMany(mappedBy = "roadmap", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoadmapMilestone> milestones;

    @OneToMany(mappedBy = "roadmap", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RoadmapNode> nodes = new ArrayList<>();

    @OneToMany(mappedBy = "roadmap", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RoadmapEdge> edges = new ArrayList<>();
}