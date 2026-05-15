package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msinterview.ai.LiveGreetingBuilder;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.ai.WhisperClient;
import tn.esprit.msinterview.dto.SessionEvent;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionQuestionOrderRepository;
import tn.esprit.msinterview.service.SessionQuestionOrderService;
import tn.esprit.msinterview.websocket.SessionEventPublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/debug")
@Slf4j
@RequiredArgsConstructor
public class DebugController {

    private final WhisperClient whisperClient;
    private final TTSClient ttsClient;
    private final InterviewQuestionRepository questionRepo;
    private final InterviewSessionRepository sessionRepo;
    private final SessionQuestionOrderRepository questionOrderRepo;
    private final SessionQuestionOrderService questionOrderService;
    private final LiveGreetingBuilder liveGreetingBuilder;
    private final SessionEventPublisher eventPublisher;

    @Value("${smarthire.audio.base-url:/interview-service/api/v1/audio}")
    private String audioBaseUrl;

    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, Object>> transcribe(@RequestParam("file") MultipartFile file) {
        log.info("[DebugController] /transcribe called - file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }

        Path tempPath = null;
        try {
            String filename = "debug_upload_" + System.currentTimeMillis() + ".wav";
            tempPath = Paths.get(System.getProperty("java.io.tmpdir"), filename);
            Files.write(tempPath, file.getBytes());
            log.info("[DebugController] Saved upload to: {}", tempPath);

            long start = System.currentTimeMillis();
            String transcript = whisperClient.transcribe(tempPath.toString());
            long elapsed = System.currentTimeMillis() - start;

            log.info("[DebugController] Transcription took {}ms: \"{}\"", elapsed, transcript);

            return ResponseEntity.ok(Map.of(
                    "transcript", transcript != null ? transcript : "",
                    "isEmpty", transcript == null || transcript.isBlank(),
                    "elapsedMs", elapsed,
                    "originalFile", file.getOriginalFilename() == null ? "" : file.getOriginalFilename()
            ));
        } catch (Exception e) {
            log.error("[DebugController] Transcription failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } finally {
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignored) {
                    // no-op
                }
            }
        }
    }

    @GetMapping("/tts")
    public ResponseEntity<Map<String, String>> testTts(
            @RequestParam(defaultValue = "Hello. This is a debug TTS test.") String text) {
        try {
            String filename = ttsClient.synthesize(text);
            if (filename == null || filename.isBlank()) {
                return ResponseEntity.internalServerError().body(Map.of("error", "TTS synthesis failed"));
            }

            String base = audioBaseUrl == null || audioBaseUrl.isBlank()
                    ? "/interview-service/api/v1/audio"
                    : audioBaseUrl.trim();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }

            return ResponseEntity.ok(Map.of(
                    "url", base + "/" + filename,
                    "filename", filename,
                    "text", text
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/behavioral-questions")
    public ResponseEntity<Map<String, Object>> listBehavioralQuestions() {
        List<InterviewQuestion> questions = questionRepo.findByTypeAndIsActiveTrue(QuestionType.BEHAVIORAL);

        return ResponseEntity.ok(Map.of(
                "count", questions.size(),
                "questions", questions.stream()
                        .map(q -> Map.of("id", q.getId(), "text", q.getQuestionText()))
                        .collect(Collectors.toList())
        ));
    }

    @PostMapping("/push-event")
    public ResponseEntity<Map<String, Object>> pushEvent(@RequestBody Map<String, Object> body) {
        Long sessionId = parseLong(body.get("sessionId"));
        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId is required"));
        }

        String eventType = asText(body.get("type"));
        if (eventType == null || eventType.isBlank()) {
            eventType = asText(body.get("eventType"));
        }
        if (eventType == null || eventType.isBlank()) {
            eventType = "DEBUG_EVENT";
        }

        String message = asText(body.get("message"));
        if (message == null || message.isBlank()) {
            message = "Debug push event";
        }

        Object payload = body.get("payload");

        SessionEvent event = SessionEvent.builder()
                .eventType(eventType)
                .sessionId(sessionId)
                .payload(payload)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();

        eventPublisher.push(sessionId, event);

        return ResponseEntity.ok(Map.of(
                "sent", true,
                "sessionId", sessionId,
                "eventType", eventType
        ));
    }

    @PostMapping("/push-session-ready")
    public ResponseEntity<Map<String, Object>> pushSessionReady(@RequestBody Map<String, Object> body) {
        Long sessionId = parseLong(body.get("sessionId"));
        if (sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId is required"));
        }

        Optional<InterviewSession> sessionOpt = sessionRepo.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found", "sessionId", sessionId));
        }

        InterviewSession session = sessionOpt.get();

        InterviewQuestion currentQuestion;
        try {
            currentQuestion = questionOrderService.getCurrentQuestion(sessionId);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Current question not found",
                    "details", ex.getMessage() == null ? "unknown" : ex.getMessage(),
                    "sessionId", sessionId
            ));
        }

        String companyName = firstNonBlank(asText(body.get("companyName")), "Tech Company");
        String targetRole = firstNonBlank(asText(body.get("targetRole")), "Candidate");
        String candidateName = firstNonBlank(asText(body.get("candidateName")), "Candidate");

        String greetingText = liveGreetingBuilder.buildGreeting(session, companyName, targetRole, currentQuestion);
        String greetingAudioUrl = resolveGreetingAudioUrl(greetingText, currentQuestion.getQuestionText());

        int totalQuestions = session.getQuestionCountRequested() != null
                ? session.getQuestionCountRequested()
                : (int) questionOrderRepo.countBySessionId(sessionId);
        int currentIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("greetingAudioUrl", greetingAudioUrl);
        payload.put("firstQuestionId", currentQuestion.getId());
        payload.put("firstQuestionText", currentQuestion.getQuestionText());
        payload.put("totalQuestions", totalQuestions);
        payload.put("currentQuestionIndex", currentIndex);
        payload.put("liveSubMode", session.getLiveSubMode() == null ? "TEST_LIVE" : session.getLiveSubMode().name());
        payload.put("candidateName", candidateName);
        payload.put("companyName", companyName);
        payload.put("targetRole", targetRole);

        eventPublisher.push(sessionId, SessionEvent.builder()
                .eventType("LIVE_SESSION_READY")
                .sessionId(sessionId)
                .payload(payload)
                .message("Debug session ready")
                .timestamp(System.currentTimeMillis())
                .build());

        return ResponseEntity.ok(Map.of(
                "sent", true,
                "eventType", "LIVE_SESSION_READY",
                "sessionId", sessionId,
                "payload", payload
        ));
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
            return "";
        }

        return buildAudioUrl(fallbackFilename);
    }

    private String buildAudioUrl(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        String base = audioBaseUrl == null || audioBaseUrl.isBlank()
                ? "/interview-service/api/v1/audio"
                : audioBaseUrl.trim();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + "/" + filename;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return null;
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
