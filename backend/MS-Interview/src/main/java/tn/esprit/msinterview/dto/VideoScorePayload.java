package tn.esprit.msinterview.dto;

import lombok.Data;

@Data
public class VideoScorePayload {
    private Long answerId;
    private Long sessionId;
    private Double eyeContactScore;
    private Double postureScore;
    private Double expressionScore;
}
