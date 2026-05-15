package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class UserRoadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long roadmapId;

    private Integer progressPercent;
    private LocalDateTime startedAt;

    @OneToMany(mappedBy = "userRoadmap", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserStepProgress> stepProgressions;
}