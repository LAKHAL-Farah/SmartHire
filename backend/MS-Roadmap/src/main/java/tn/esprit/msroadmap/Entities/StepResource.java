package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
@EntityListeners(AuditingEntityListener.class)
public class StepResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id")
    @ToString.Exclude
    private RoadmapStep step;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.ResourceType type;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.ResourceProvider provider;

    private String title;
    private String url;
    private Double rating;
    private Double durationHours;
    private Double price;
    private boolean isFree = false;
    private String externalId;

    @CreatedDate
    private LocalDateTime createdAt;
}
