package tn.esprit.msinterview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAiStubConfig.class)
class MLPipelineWebSocketTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;

    @AfterEach
    void tearDown() {
        try {
            if (stompSession != null && stompSession.isConnected()) {
                stompSession.disconnect();
            }
        } catch (Exception ignored) {
            // no-op
        }
    }

    @Test
    void subscribe_receivesSessionStartedEvent() throws Exception {
        long predictedSessionId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1 FROM interview_sessions",
                Long.class
        );

        BlockingQueue<Map<String, Object>> events = connectAndSubscribe(predictedSessionId);

        Map<String, Object> started = startSession();
        long actualSessionId = asLong(started.get("id"));
        assertThat(actualSessionId).isEqualTo(predictedSessionId);

        Map<String, Object> event = pollEventByType(events, "SESSION_STARTED", 10);
        assertThat(event).isNotNull();
        assertThat(String.valueOf(event.get("eventType"))).isEqualTo("SESSION_STARTED");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload).containsKey("firstQuestion");
        assertThat(payload).containsKey("ttsAudioUrl");

        Object roleType = payload.get("roleType");
        assertThat(String.valueOf(roleType)).isEqualTo("AI");
    }

    @Test
    void submitAnswer_receivesEvaluationReadyEvent() throws Exception {
        Map<String, Object> started = startSession();
        long sessionId = asLong(started.get("id"));
        long questionId = getCurrentQuestionId(sessionId);

        BlockingQueue<Map<String, Object>> events = connectAndSubscribe(sessionId);

        long answerId = submitAnswer(sessionId, questionId,
                "I would use XGBoost, engineer features, evaluate with F1 and AUC, then deploy via FastAPI.");

        Map<String, Object> event = pollEventByType(events, "EVALUATION_READY", 20);
        assertThat(event).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(asLong(payload.get("answerId"))).isEqualTo(answerId);

        double contentScore = asDouble(payload.get("contentScore"));
        assertThat(contentScore).isBetween(0.0, 10.0);
    }

    @Test
    void mlConceptExtracted_followUpEventPushed() throws Exception {
        Map<String, Object> started = startSession();
        long sessionId = asLong(started.get("id"));
        long questionId = getCurrentQuestionId(sessionId);

        BlockingQueue<Map<String, Object>> events = connectAndSubscribe(sessionId);

        submitAnswer(sessionId, questionId,
                "I would use XGBoost with feature encoding and normalization, evaluate with F1 and AUC, and deploy with FastAPI.");

        Map<String, Object> followUpEvent = pollEventByType(events, "FOLLOW_UP", 20);
        assertThat(followUpEvent).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) followUpEvent.get("payload");
        String followUpText = String.valueOf(payload.get("followUpGenerated"));

        assertThat(followUpText).isNotBlank();
    }

    private BlockingQueue<Map<String, Object>> connectAndSubscribe(long sessionId) throws Exception {
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        List<Transport> transports = List.of(new WebSocketTransport(webSocketClient));
        SockJsClient sockJsClient = new SockJsClient(transports);

        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<StompSession> future = stompClient.connectAsync(
            "ws://localhost:" + port + "/ws",
            new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {
            }
        );

        stompSession = future.get(10, TimeUnit.SECONDS);
        assertThat(stompSession.isConnected()).isTrue();

        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        stompSession.subscribe("/topic/session/" + sessionId, new MapFrameHandler(queue));
        return queue;
    }

    private Map<String, Object> startSession() {
        Map<String, Object> body = Map.of(
                "userId", 1,
                "careerPathId", 1,
                "role", "AI",
                "mode", "PRACTICE",
                "type", "TECHNICAL",
                "questionCount", 5
        );

            var response = restTemplate.postForEntity(httpUrl("/api/v1/sessions/start"), body, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private long getCurrentQuestionId(long sessionId) {
            var response = restTemplate.getForEntity(
                httpUrl("/api/v1/sessions/{sessionId}/questions/current"),
                Map.class,
                sessionId
            );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return asLong(response.getBody().get("id"));
    }

    private long submitAnswer(long sessionId, long questionId, String answerText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("questionId", questionId);
        body.put("answerText", answerText);
        body.put("videoUrl", null);
        body.put("audioUrl", null);

        var response = restTemplate.postForEntity(httpUrl("/api/v1/answers/submit"), body, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return asLong(response.getBody().get("id"));
    }

    private String httpUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private Map<String, Object> pollEventByType(BlockingQueue<Map<String, Object>> queue, String eventType, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);

        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> event = queue.poll(1, TimeUnit.SECONDS);
            if (event == null) {
                continue;
            }
            if (eventType.equals(String.valueOf(event.get("eventType")))) {
                return event;
            }
        }

        return null;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static class MapFrameHandler implements StompFrameHandler {
        private final BlockingQueue<Map<String, Object>> queue;

        private MapFrameHandler(BlockingQueue<Map<String, Object>> queue) {
            this.queue = queue;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload instanceof Map<?, ?> map) {
                queue.offer((Map<String, Object>) map);
            }
        }
    }
}
