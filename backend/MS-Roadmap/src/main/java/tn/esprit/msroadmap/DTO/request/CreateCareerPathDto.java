package tn.esprit.msroadmap.DTO.request;

import lombok.Data;

@Data
public class CreateCareerPathDto {
    private String title;
    private String description;
    private String defaultTopics;
    private String difficulty;
    private Integer estimatedWeeks;
}
