package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDate;
import tn.esprit.msroadmap.Enums.PaceStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaceSnapshotDto {
    private Long id;
    private Long roadmapId;
    private LocalDate snapshotDate;
    private int plannedSteps;
    private int completedSteps;
    private int deltaDays;
    private PaceStatus paceStatus;
    private String catchUpPlanNote;
}
