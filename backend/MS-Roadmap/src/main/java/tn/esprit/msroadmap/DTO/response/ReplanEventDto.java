package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplanEventDto {
    private Long id;
    private Long roadmapId;
    private Long triggerStepId;
    private String reason;
    private LocalDateTime replannedAt;
}
