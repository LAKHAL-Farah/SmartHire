package tn.esprit.msroadmap.DTO.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrySubmissionRequestDto {
    private String repoUrl;
}
