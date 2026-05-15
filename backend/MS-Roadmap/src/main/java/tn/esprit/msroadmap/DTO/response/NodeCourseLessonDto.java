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
public class NodeCourseLessonDto {
    private String sectionTitle;
    private String explanation;
    private String miniExample;
    private String codeSnippet;
    private List<String> commonPitfalls;
    private List<String> practiceTasks;
}
