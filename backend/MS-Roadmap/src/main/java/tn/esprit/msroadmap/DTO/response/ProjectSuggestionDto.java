package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import tn.esprit.msroadmap.Enums.DifficultyLevel;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSuggestionDto {
    private Long id;
    private Long stepId;
    private String title;
    private String description;
    private DifficultyLevel difficulty;
    private List<String> githubTopics;
    private List<String> techStack;
    private int estimatedDays;
    private String alignedCareerPath;
    private LocalDateTime createdAt;
}
