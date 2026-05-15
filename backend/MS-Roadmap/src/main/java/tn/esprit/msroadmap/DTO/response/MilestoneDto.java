package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneDto {
    private Long id;
    private Long roadmapId;
    private String title;
    private String description;
    private int stepThreshold;
    private LocalDateTime reachedAt;
    private boolean certificateIssued;
}
