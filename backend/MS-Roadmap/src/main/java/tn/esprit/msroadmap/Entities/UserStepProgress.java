package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class UserStepProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long stepId;
    private String status;
    private LocalDateTime completeAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_roadmap_id")
    @ToString.Exclude
    private UserRoadmap userRoadmap;
}