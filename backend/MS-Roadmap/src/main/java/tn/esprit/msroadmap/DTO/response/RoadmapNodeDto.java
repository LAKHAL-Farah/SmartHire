package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprit.msroadmap.Enums.DifficultyLevel;
import tn.esprit.msroadmap.Enums.NodeType;
import tn.esprit.msroadmap.Enums.StepStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapNodeDto {
    private Long id;
    private String nodeId;
    private String title;
    private String description;
    private String objective;
    private NodeType type;
    private DifficultyLevel difficulty;
    private StepStatus status;
    private int stepOrder;
    private int estimatedDays;
    private Integer actualDays;
    private String technologies;
    private Double positionX;
    private Double positionY;
    private LocalDateTime unlockedAt;
    private LocalDateTime completedAt;
}
