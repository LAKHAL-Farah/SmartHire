package tn.esprit.msroadmap.DTO.request;

import lombok.*;
import tn.esprit.msroadmap.Enums.ResourceProvider;
import tn.esprit.msroadmap.Enums.ResourceType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddResourceRequestDto {
    private ResourceType type;
    private ResourceProvider provider;
    private String title;
    private String url;
    private Double rating;
    private Double durationHours;
    private Double price;
    private boolean isFree;
    private String externalId;
}
