package tn.esprit.msroadmap.DTO.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressSummaryDto {
    private Long roadmapId;
    private int totalSteps;
    private int completedSteps;
    private double progressPercent;
    private int streakDays;
    private StepResponse currentStep;
}
