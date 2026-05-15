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
public class NodeCourseContentDto {
    private Long historyId;
    private Long nodeId;
    private String nodeTitle;
    private String courseTitle;
    private String intro;
    private String difficulty;
    private String technologies;
    private boolean aiGenerated;
    private LocalDateTime generatedAt;
    private List<NodeCourseLessonDto> lessons;
    private List<NodeCourseCheckpointDto> checkpoints;
    private List<String> cheatSheet;
    private String nextNodeFocus;
}
