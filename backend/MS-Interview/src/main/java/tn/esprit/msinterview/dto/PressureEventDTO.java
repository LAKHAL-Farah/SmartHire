package tn.esprit.msinterview.dto;

import lombok.*;
import tn.esprit.msinterview.entity.enumerated.PressureEventType;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PressureEventDTO {
    private Long id;
    private Long sessionId;
    private LocalDateTime triggeredAt;
    private PressureEventType eventType;
    private Long questionIdAtTrigger;
    private boolean candidateReacted;
    private Long reactionTimeMs;
}
