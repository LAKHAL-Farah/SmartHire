package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprit.msroadmap.Enums.RoadmapStatus;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapVisualResponse {
    private Long roadmapId;
    private String title;
    private String description;
    private RoadmapStatus status;
    private int totalNodes;
    private int completedNodes;
    private double progressPercent;
    private int streakDays;
    private int longestStreak;
    private List<RoadmapNodeDto> nodes;
    private List<RoadmapEdgeDto> edges;
}
