package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionDto {
    private Long id;
    private Long userId;
    private String careerPath;
    private String difficulty;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Double finalScore;
    private Integer totalQuestions;
    private Integer answeredQuestions;
}
