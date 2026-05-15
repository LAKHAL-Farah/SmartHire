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
public class NodeQuizResponseDto {
    private Long nodeId;
    private String nodeTitle;
    private int passThreshold;
    private boolean aiGenerated;
    private List<NodeQuizQuestionDto> questions;
}
