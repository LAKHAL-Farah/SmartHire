package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class RoadmapReplanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    @ToString.Exclude
    private Roadmap roadmap;

    private Long triggerStepId;
    private String reason;

    @Column(columnDefinition = "LONGTEXT")
    private String previousPlan;

    @Column(columnDefinition = "LONGTEXT")
    private String newPlan;

    @CreatedDate
    private LocalDateTime replannedAt;
}
