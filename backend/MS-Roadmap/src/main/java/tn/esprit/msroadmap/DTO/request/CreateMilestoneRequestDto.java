package tn.esprit.msroadmap.DTO.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMilestoneRequestDto {
    private String title;
    private String description;
    private int stepThreshold;
}
