package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeProjectValidationResponseDto {
    private Long nodeId;
    private String projectTitle;
    private int passThreshold;
    private int scorePercent;
    private boolean passed;
    private boolean aiGenerated;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
    private List<String> nextSteps;
}
