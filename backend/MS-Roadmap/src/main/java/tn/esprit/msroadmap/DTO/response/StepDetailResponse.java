package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepDetailResponse {
    private Long id;
    private Integer stepOrder;
    private String title;
    private String objective;
    private Integer estimatedDays;
    private Integer actualDays;
    private String status;
    private java.time.LocalDateTime unlockedAt;
    private java.time.LocalDateTime completedAt;
    private List<StepResourceDto> resources;
    private List<ProjectSuggestionDto> projectSuggestions;
}
