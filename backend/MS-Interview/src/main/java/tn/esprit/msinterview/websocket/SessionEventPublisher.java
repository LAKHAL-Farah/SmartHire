package tn.esprit.msinterview.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tn.esprit.msinterview.dto.SessionEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionEventPublisher {

    private static final String SESSION_TOPIC = "/topic/session/";

    private final SimpMessagingTemplate messagingTemplate;

    public void push(Long sessionId, SessionEvent event) {
        if (sessionId == null || event == null) {
            return;
        }

        String destination = SESSION_TOPIC + sessionId;
        log.debug("Pushing {} to {}", event.getEventType(), destination);
        messagingTemplate.convertAndSend(destination, event);
    }

    public void pushEvaluationReady(Long sessionId, Object evaluation) {
        push(sessionId, SessionEvent.evaluationReady(sessionId, evaluation));
    }

    public void pushNextQuestion(Long sessionId, Object question) {
        push(sessionId, SessionEvent.nextQuestion(sessionId, question));
    }

    public void pushSessionStarted(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.sessionStarted(sessionId, payload));
    }

    public void pushFollowUp(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.followUp(sessionId, payload));
    }

    public void pushSessionCompleted(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.sessionCompleted(sessionId, payload));
    }

    public void pushFillerAudio(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.builder()
                .eventType("FILLER_AUDIO")
                .sessionId(sessionId)
                .payload(payload)
                .message("Filler audio")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void pushLiveAiSpeech(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.builder()
                .eventType("LIVE_AI_SPEECH")
                .sessionId(sessionId)
                .payload(payload)
                .message("Live AI speech")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void pushLiveFeedback(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.builder()
                .eventType("LIVE_FEEDBACK")
                .sessionId(sessionId)
                .payload(payload)
                .message("Live feedback")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void pushReportReady(Long sessionId, Object payload) {
        push(sessionId, SessionEvent.builder()
                .eventType("REPORT_READY")
                .sessionId(sessionId)
                .payload(payload)
                .message("Report ready")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void pushError(Long sessionId, String message) {
        push(sessionId, SessionEvent.error(sessionId, message));
    }
}
