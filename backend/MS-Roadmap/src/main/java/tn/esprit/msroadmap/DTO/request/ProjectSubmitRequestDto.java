package tn.esprit.msroadmap.DTO.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSubmitRequestDto {
    private Long userId;
    private Long projectSuggestionId;
    private String repoUrl;
}
