package tn.esprit.msroadmap.DTO.request;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapGenerationRequestDto {
    private Long userId;
    private Long careerPathId;
    private String careerPathName;
    private List<String> skillGaps;
    private List<String> strongSkills;
    private String experienceLevel;
    private int weeklyHoursAvailable;
    private String preferredLanguage;
}
