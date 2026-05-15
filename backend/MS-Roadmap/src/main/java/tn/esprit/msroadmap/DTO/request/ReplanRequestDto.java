package tn.esprit.msroadmap.DTO.request;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplanRequestDto {
    private Long roadmapId;
    private List<String> newSkillGaps;
    private List<String> newStrongSkills;
    private String experienceLevel;
    private Integer weeklyHoursAvailable;
    private String preferredLanguage;
}
