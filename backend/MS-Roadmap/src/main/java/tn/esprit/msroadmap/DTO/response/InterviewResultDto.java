package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewResultDto {
    private Long sessionId;
    private String status;
    private Double finalScore;
    private int totalQuestions;
    private int answeredQuestions;
    private List<AnswerResultDto> answers;
}
