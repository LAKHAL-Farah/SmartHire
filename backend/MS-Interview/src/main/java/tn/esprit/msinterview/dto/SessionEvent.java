package tn.esprit.msinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvent {

    private String eventType;
    private Long sessionId;
    private Object payload;
    private String message;
    private long timestamp;

    public static SessionEvent evaluationReady(Long sessionId, Object evaluation) {
        return SessionEvent.builder()
                .eventType("EVALUATION_READY")
                .sessionId(sessionId)
                .payload(evaluation)
                .message("Evaluation complete")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SessionEvent nextQuestion(Long sessionId, Object question) {
        return SessionEvent.builder()
                .eventType("NEXT_QUESTION")
                .sessionId(sessionId)
                .payload(question)
                .message("Next question ready")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SessionEvent sessionStarted(Long sessionId, Object payload) {
        return SessionEvent.builder()
                .eventType("SESSION_STARTED")
                .sessionId(sessionId)
                .payload(payload)
                .message("Session started")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SessionEvent followUp(Long sessionId, Object payload) {
        return SessionEvent.builder()
                .eventType("FOLLOW_UP")
                .sessionId(sessionId)
                .payload(payload)
                .message("Follow-up generated")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SessionEvent sessionCompleted(Long sessionId, Object payload) {
        return SessionEvent.builder()
                .eventType("SESSION_COMPLETED")
                .sessionId(sessionId)
                .payload(payload)
                .message("Session complete")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SessionEvent error(Long sessionId, String errorMessage) {
        return SessionEvent.builder()
                .eventType("ERROR")
                .sessionId(sessionId)
                .payload(null)
                .message(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
