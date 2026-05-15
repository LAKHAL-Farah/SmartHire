package tn.esprit.msinterview.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.LiveGreetingBuilder;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.dto.SessionEvent;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewReport;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionQuestionOrder;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionQuestionOrderRepository;
import tn.esprit.msinterview.service.InterviewReportService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Optional;

@Controller
@Slf4j
@RequiredArgsConstructor
public class LiveAudioWebSocketController {

    private final LiveAudioBuffer audioBuffer;
    private final InterviewSessionRepository sessionRepo;
    private final SessionQuestionOrderRepository sqoRepository;
    private final SessionEventPublisher eventPublisher;
    private final TTSClient ttsClient;
    private final LiveGreetingBuilder greetingBuilder;
    private final InterviewReportService reportService;

    @Value("${smarthire.audio.base-url:/interview-service/api/v1/audio}")
    private String audioBaseUrl;

    @MessageMapping("/session/{sessionId}/bootstrap")
    @Transactional(readOnly = true)
    public void bootstrap(@DestinationVariable Long sessionId) {
        log.info("[WS] Bootstrap: session={}", sessionId);

        InterviewSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) {
            eventPublisher.pushError(sessionId, "Live session not found.");
            return;
        }

        audioBuffer.initSession(sessionId);

        int currentIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        Optional<SessionQuestionOrder> currentOrder = sqoRepository.findBySessionIdAndQuestionOrder(sessionId, currentIndex);
        if (currentOrder.isEmpty()) {
            currentOrder = sqoRepository.findBySessionIdAndQuestionOrder(sessionId, 0);
            currentIndex = 0;
        }

        if (currentOrder.isEmpty() || currentOrder.get().getQuestion() == null) {
            eventPublisher.pushError(sessionId, "No questions found for this live session.");
            return;
        }

        InterviewQuestion firstQuestion = currentOrder.get().getQuestion();
        int totalQuestions = session.getQuestionCountRequested() != null
                ? session.getQuestionCountRequested()
                : (int) sqoRepository.countBySessionId(sessionId);

        String greetingText = greetingBuilder.buildGreeting(
                session,
                "Tech Company",
                "Candidate",
                firstQuestion
        );

        String greetingAudioUrl = resolveGreetingAudioUrl(greetingText, firstQuestion.getQuestionText());

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("greetingAudioUrl", greetingAudioUrl);
        payload.put("firstQuestionId", firstQuestion.getId());
        payload.put("firstQuestionText", firstQuestion.getQuestionText());
        payload.put("totalQuestions", totalQuestions);
        payload.put("currentQuestionIndex", currentIndex);
        payload.put("liveSubMode", session.getLiveSubMode() == null ? "TEST_LIVE" : session.getLiveSubMode().name());

        eventPublisher.push(sessionId, SessionEvent.builder()
                .eventType("LIVE_SESSION_READY")
                .sessionId(sessionId)
                .payload(payload)
                .message("Live session ready")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    @MessageMapping("/session/{sessionId}/audio-chunk")
    public void receiveChunk(@DestinationVariable Long sessionId, byte[] chunk) {
        byte[] normalized = normalizeChunkPayload(chunk, sessionId);
        audioBuffer.receiveChunk(sessionId, normalized);
    }

    @MessageMapping("/session/{sessionId}/end-turn")
    public void endTurn(@DestinationVariable Long sessionId) {
        log.info("[WS] End-turn: session={}", sessionId);
        audioBuffer.forceSeal(sessionId);
    }

    @MessageMapping("/session/{sessionId}/retry")
    public void retry(@DestinationVariable Long sessionId) {
        log.info("[WS] Retry: session={}", sessionId);

        sessionRepo.findById(sessionId).ifPresent(session -> {
            session.setStatus(SessionStatus.IN_PROGRESS);
            int retries = session.getRetryCountTotal() == null ? 0 : session.getRetryCountTotal();
            session.setRetryCountTotal(retries + 1);
            sessionRepo.save(session);

            String text = greetingBuilder.buildRetryPrompt();
            LinkedHashMap<String, Object> payload = buildSpeechPayload(
                    buildAudioUrl(ttsClient.synthesize(text)),
                    text,
                    false,
                    true,
                    false,
                    null,
                    null,
                    safeDisplayIndex(session),
                    session.getQuestionCountRequested()
            );
            eventPublisher.pushLiveAiSpeech(sessionId, payload);
        });
    }

    @MessageMapping("/session/{sessionId}/continue")
    public void continueNext(@DestinationVariable Long sessionId) {
        log.info("[WS] Continue: session={}", sessionId);

        sessionRepo.findById(sessionId).ifPresent(session -> {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionRepo.save(session);

            int current = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
            int nextIndex = current + 1;

            Optional<SessionQuestionOrder> nextOpt = sqoRepository.findBySessionIdAndQuestionOrder(sessionId, nextIndex);
            if (nextOpt.isEmpty()) {
                String closing = greetingBuilder.buildClosing(session);
                session.setStatus(SessionStatus.COMPLETED);
                session.setEndedAt(LocalDateTime.now());
                sessionRepo.save(session);

                LinkedHashMap<String, Object> payload = buildSpeechPayload(
                        buildAudioUrl(ttsClient.synthesize(closing)),
                        closing,
                        false,
                        false,
                        true,
                        null,
                        null,
                        session.getQuestionCountRequested(),
                        session.getQuestionCountRequested()
                );
                eventPublisher.pushLiveAiSpeech(sessionId, payload);

                InterviewReport report = reportService.generateReport(sessionId);
                LinkedHashMap<String, Object> reportPayload = new LinkedHashMap<>();
                reportPayload.put("sessionId", sessionId);
                reportPayload.put("reportId", report.getId());
                reportPayload.put("finalScore", report.getFinalScore());
                eventPublisher.pushReportReady(sessionId, reportPayload);

                audioBuffer.clearSession(sessionId);
                return;
            }

            InterviewQuestion next = nextOpt.get().getQuestion();
            session.setCurrentQuestionIndex(nextIndex);
            sessionRepo.save(session);

            String text = "Alright, moving on. " + next.getQuestionText();
            LinkedHashMap<String, Object> payload = buildSpeechPayload(
                    buildAudioUrl(ttsClient.synthesize(text)),
                    text,
                    false,
                    false,
                    false,
                    next.getId(),
                    next.getQuestionText(),
                    safeDisplayIndex(session),
                    session.getQuestionCountRequested()
            );
            eventPublisher.pushLiveAiSpeech(sessionId, payload);
        });
    }

    private LinkedHashMap<String, Object> buildSpeechPayload(String audioUrl,
                                                             String text,
                                                             boolean isFollowUp,
                                                             boolean isRetry,
                                                             boolean isClosing,
                                                             Long nextQuestionId,
                                                             String nextQuestionText,
                                                             Integer currentQuestionIndex,
                                                             Integer totalQuestions) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("audioUrl", audioUrl);
        payload.put("text", text);
        payload.put("isFollowUp", isFollowUp);
        payload.put("isRetry", isRetry);
        payload.put("isClosing", isClosing);
        payload.put("nextQuestionId", nextQuestionId);
        payload.put("nextQuestionText", nextQuestionText);
        payload.put("currentQuestionIndex", currentQuestionIndex);
        payload.put("totalQuestions", totalQuestions);
        payload.put("transcript", "");
        return payload;
    }

    private int safeDisplayIndex(InterviewSession session) {
        int current = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        return current + 1;
    }

    private String buildAudioUrl(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String normalizedBase = audioBaseUrl.endsWith("/")
                ? audioBaseUrl.substring(0, audioBaseUrl.length() - 1)
                : audioBaseUrl;
        return normalizedBase + "/" + filename;
    }

    private String resolveGreetingAudioUrl(String greetingText, String firstQuestionText) {
        String filename = ttsClient.synthesize(greetingText);
        if (filename != null && !filename.isBlank()) {
            return buildAudioUrl(filename);
        }

        String safeQuestion = firstQuestionText == null ? "" : firstQuestionText.trim();
        if (safeQuestion.length() > 220) {
            safeQuestion = safeQuestion.substring(0, 220);
        }

        String fallback = safeQuestion.isBlank()
                ? "Welcome. Let us begin the interview."
                : "Welcome. First question: " + safeQuestion;

        String fallbackFilename = ttsClient.synthesize(fallback);
        if (fallbackFilename == null || fallbackFilename.isBlank()) {
            return null;
        }

        return buildAudioUrl(fallbackFilename);
    }

    private byte[] normalizeChunkPayload(byte[] chunk, Long sessionId) {
        if (chunk == null || chunk.length == 0) {
            return chunk;
        }

        String text = new String(chunk, StandardCharsets.UTF_8);
        if (!text.startsWith("b64:")) {
            return chunk;
        }

        String encoded = text.substring(4).trim();
        if (encoded.isEmpty()) {
            return new byte[0];
        }

        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            log.warn("[WS] Invalid base64 audio chunk for session {}: {}", sessionId, ex.getMessage());
            return new byte[0];
        }
    }
}
