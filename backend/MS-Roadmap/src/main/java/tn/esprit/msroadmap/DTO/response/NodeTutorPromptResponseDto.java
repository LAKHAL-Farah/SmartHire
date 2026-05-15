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
public class NodeTutorPromptResponseDto {
    private Long nodeId;
    private String nodeTitle;
    private String prompt;
    private String answer;
    private List<String> keyTakeaways;
    private List<String> nextActions;
    private boolean aiGenerated;
    private LocalDateTime respondedAt;
}
