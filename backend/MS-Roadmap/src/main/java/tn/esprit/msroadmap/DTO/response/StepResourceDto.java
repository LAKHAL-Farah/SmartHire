package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;
import tn.esprit.msroadmap.Enums.ResourceProvider;
import tn.esprit.msroadmap.Enums.ResourceType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResourceDto {
    private Long id;
    private Long stepId;
    private ResourceType type;
    private ResourceProvider provider;
    private String title;
    private String url;
    private Double rating;
    private Double durationHours;
    private Double price;
    private boolean isFree;
    private String externalId;
    private LocalDateTime createdAt;
}
