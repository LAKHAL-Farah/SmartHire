package tn.esprit.msinterview.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import tn.esprit.msinterview.dto.VideoScorePayload;
import tn.esprit.msinterview.service.AnswerEvaluationService;

@Controller
@RequiredArgsConstructor
@Slf4j
public class InterviewWebSocketController {

    private final AnswerEvaluationService evaluationService;

    @MessageMapping("/session/video-scores")
    public void receiveVideoScores(@Payload VideoScorePayload payload) {
        if (payload == null || payload.getAnswerId() == null) {
            return;
        }

        log.debug("Received video scores for answer {}", payload.getAnswerId());

        evaluationService.updateVideoScores(
                payload.getAnswerId(),
                payload.getPostureScore(),
                payload.getEyeContactScore(),
                payload.getExpressionScore()
        );
    }
}
