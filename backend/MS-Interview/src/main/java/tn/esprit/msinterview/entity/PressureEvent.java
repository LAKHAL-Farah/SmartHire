package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msinterview.entity.enumerated.PressureEventType;

import java.time.LocalDateTime;

@Entity
@Table(name = "pressure_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PressureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    private LocalDateTime triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PressureEventType eventType;

    private Long questionIdAtTrigger;

    @com.fasterxml.jackson.annotation.JsonProperty("candidateReacted")
    @com.fasterxml.jackson.annotation.JsonAlias("reacted")
    private boolean candidateReacted;
    private Long reactionTimeMs;
}
