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
public class RoadmapNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id")
    @ToString.Exclude
    private Roadmap roadmap;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.NotificationType type;

    private String message;
    private boolean isRead = false;

    @CreatedDate
    private LocalDateTime createdAt;
}
