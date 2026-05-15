package tn.esprit.msinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveSessionStartResponse {
    private Long sessionId;
    private String greetingAudioUrl;
    private String firstQuestionText;
    private Long firstQuestionId;
    private int totalQuestions;
    private String liveSubMode;
    private String status;
}
