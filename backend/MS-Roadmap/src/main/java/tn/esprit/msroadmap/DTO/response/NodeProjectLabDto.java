package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeProjectLabDto {
    private Long historyId;
    private Long nodeId;
    private String nodeTitle;
    private String projectTitle;
    private String scenario;
    private String language;
    private int estimatedHours;
    private int passThreshold;
    private boolean aiGenerated;
    private List<String> userStories;
    private List<String> acceptanceCriteria;
    private List<String> stretchGoals;
    private String starterCode;
    private LocalDateTime generatedAt;
}
