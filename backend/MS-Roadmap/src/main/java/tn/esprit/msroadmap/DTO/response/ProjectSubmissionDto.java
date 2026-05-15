package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import tn.esprit.msroadmap.Enums.SubmissionStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSubmissionDto {
    private Long id;
    private Long userId;
    private Long projectSuggestionId;
    private String repoUrl;
    private SubmissionStatus status;
    private Integer score;
    private String aiFeedback;
    private Integer readmeScore;
    private Integer structureScore;
    private Integer testScore;
    private Integer ciScore;
    private List<String> recommendations;
    private int retryCount;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
