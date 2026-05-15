package tn.esprit.msinterview.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.esprit.msinterview.ai.FillerAudioCache;
import tn.esprit.msinterview.ai.LiveGreetingBuilder;
import tn.esprit.msinterview.ai.LiveResponseBuilder;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.ai.WhisperClient;
import tn.esprit.msinterview.dto.SessionEvent;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewReport;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.entity.SessionQuestionOrder;
import tn.esprit.msinterview.entity.enumerated.LiveSubMode;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.repository.AnswerEvaluationRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.repository.SessionQuestionOrderRepository;
import tn.esprit.msinterview.service.EvaluationEngine;
import tn.esprit.msinterview.service.InterviewReportService;
import tn.esprit.msinterview.service.SessionQuestionOrderService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.LinkedHashMap;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class LiveAnswerProcessor {

    private final WhisperClient whisperClient;
    private final SessionEventPublisher eventPublisher;
    private final FillerAudioCache fillerCache;
    private final LiveGreetingBuilder greetingBuilder;
    private final LiveResponseBuilder responseBuilder;
    private final TTSClient ttsClient;
    private final InterviewSessionRepository sessionRepo;
    private final SessionAnswerRepository answerRepo;
    private final AnswerEvaluationRepository answerEvaluationRepo;
    private final SessionQuestionOrderRepository sqoRepository;
    private final SessionQuestionOrderService sqoService;
    private final EvaluationEngine evaluationEngine;
    private final InterviewReportService reportService;
    private final @Qualifier("liveExecutor") Executor liveExecutor;
    private final @Lazy LiveAudioBuffer audioBuffer;

    @Value("${smarthire.audio.base-url:/interview-service/audio}")
    private String audioBaseUrl;

    @Value("${smarthire.live.practice.feedback.threshold:6.0}")
    private double feedbackThreshold;

    @Async("liveExecutor")
    @Transactional
    public void processAudio(Long sessionId, byte[] audioData) {
        int bytes = audioData == null ? 0 : audioData.length;
        log.info("[Processor] processAudio: session={} bytes={}", sessionId, bytes);

        InterviewSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) {
            log.error("[Processor] Session not found: {}", sessionId);
            return;
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            log.warn("[Processor] Session {} not IN_PROGRESS ({}), skipping", sessionId, session.getStatus());
            return;
        }

        String fillerUrl = fillerCache.getRandomFillerUrl();
        if (fillerUrl != null) {
            LinkedHashMap<String, Object> fillerPayload = new LinkedHashMap<>();
            fillerPayload.put("audioUrl", fillerUrl);
            eventPublisher.pushFillerAudio(sessionId, fillerPayload);
            log.info("[Processor] Filler pushed: {}", fillerUrl);
        } else {
            log.warn("[Processor] Filler cache empty - no filler played");
        }

        String audioPath;
        try {
            audioPath = writeTempFile(audioData, sessionId);
            log.info("[Processor] Audio saved: {} ({} bytes)", audioPath, bytes);
        } catch (RuntimeException ex) {
            log.error("[Processor] Failed to write audio temp file: {}", ex.getMessage());
            pushFallbackQuestion(session, "[audio write failed]");
            return;
        }

        String transcript;
        try {
            log.info("[Processor] Transcribing...");
            if (bytes == 0) {
                transcript = "[silence]";
            } else {
                String detected = whisperClient.transcribe(audioPath);
                transcript = (detected == null || detected.isBlank())
                        ? "[empty transcript]"
                        : detected;
            }
            log.info("[Processor] Transcript: \"{}\"", transcript);
        } catch (Exception ex) {
            log.error("[Processor] Whisper failed: {}", ex.getMessage());
            transcript = "[transcription failed]";
        }

        InterviewQuestion currentQuestion;
        try {
            currentQuestion = sqoService.getCurrentQuestion(session.getId());
            if (currentQuestion == null) {
                log.error("[Processor] No current question for session={}", sessionId);
                pushFallbackQuestion(session, transcript);
                return;
            }
        } catch (Exception ex) {
            log.error("[Processor] Failed to fetch current question: {}", ex.getMessage());
            pushFallbackQuestion(session, transcript);
            return;
        }

        SessionAnswer answer = new SessionAnswer();
        answer.setSession(session);
        answer.setQuestion(currentQuestion);
        answer.setAnswerText(transcript);
        answer.setTimeSpentSeconds(estimateSeconds(bytes));
        answer.setSubmittedAt(LocalDateTime.now());
        answer.setRetryCount(0);

        SessionAnswer saved = answer;
        boolean answerPersisted = false;
        try {
            saved = answerRepo.save(answer);
            answerPersisted = true;
            log.info("[Processor] SessionAnswer saved: id={}", saved.getId());
        } catch (Exception ex) {
            log.error("[Processor] Failed to save SessionAnswer: {}", ex.getMessage());
            saved.setId(-1L);
        }

        publishTranscriptEvent(sessionId, saved.getId(), transcript, bytes, currentQuestion.getId());

        AnswerEvaluation evaluation = evaluateAnswer(saved, answerPersisted);

        boolean isPractice = session.getLiveSubMode() == LiveSubMode.PRACTICE_LIVE;
        boolean lowScore = safeOverallScore(evaluation) < feedbackThreshold;

        if (isPractice && lowScore) {
            handlePracticeFeedback(session, saved, evaluation, transcript);
        } else {
            handleAdvance(session, evaluation, transcript);
        }
    }

    private AnswerEvaluation evaluateAnswer(SessionAnswer saved, boolean answerPersisted) {
        try {
            log.info("[Processor] Evaluating answer {} via Nvidia API...", saved.getId());
            AnswerEvaluation evaluation = evaluationEngine.evaluateTextAnswer(saved);
            if (evaluation == null) {
                throw new IllegalStateException("Evaluation engine returned null evaluation");
            }
            if (evaluation.getAnswer() == null) {
                evaluation.setAnswer(saved);
            }
            if (evaluation.getOverallScore() == null) {
                evaluation.setOverallScore(round2(average(
                        evaluation.getContentScore(),
                        evaluation.getClarityScore(),
                        evaluation.getTechnicalScore()
                )));
            }

            if (answerPersisted) {
                evaluation = answerEvaluationRepo.save(evaluation);
            }

            log.info("[Processor] Evaluation complete: answerId={} score={}",
                    saved.getId(),
                    evaluation.getOverallScore());
            return evaluation;
        } catch (Exception ex) {
            log.error("[Processor] Nvidia evaluation failed: {}", ex.getMessage(), ex);

            AnswerEvaluation fallback = AnswerEvaluation.builder()
                    .answer(saved)
                    .contentScore(5.0)
                    .clarityScore(5.0)
                    .technicalScore(5.0)
                    .confidenceScore(5.0)
                    .toneScore(5.0)
                    .postureScore(5.0)
                    .overallScore(5.0)
                    .aiFeedback("Evaluation unavailable. Score defaulted to 5.")
                    .followUpGenerated(null)
                    .build();

            if (answerPersisted) {
                try {
                    fallback = answerEvaluationRepo.save(fallback);
                } catch (Exception saveEx) {
                    log.error("[Processor] Failed to save fallback evaluation: {}", saveEx.getMessage());
                }
            }

            return fallback;
        }
    }

    private void handlePracticeFeedback(InterviewSession session,
                                        SessionAnswer answer,
                                        AnswerEvaluation eval,
                                        String transcript) {
        double score = safeOverallScore(eval);
        log.info("[Processor] Score {} < {} in PRACTICE mode - sending feedback", score, feedbackThreshold);

        String feedbackSpeech = responseBuilder.buildFeedbackSpeech(eval.getAiFeedback(), score);
        String feedbackUrl = synthesizeAndGetUrl(feedbackSpeech);
        if (feedbackUrl == null) {
            log.error("[Processor] TTS failed for feedback - falling back to advance");
            handleAdvance(session, eval, transcript);
            return;
        }

        session.setStatus(SessionStatus.PAUSED);
        sessionRepo.save(session);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("answerId", answer.getId());
        payload.put("audioUrl", feedbackUrl);
        payload.put("feedbackText", feedbackSpeech);
        payload.put("score", score);
        payload.put("aiFeedback", eval.getAiFeedback() != null ? eval.getAiFeedback() : "");
        payload.put("currentQuestionIndex", safeDisplayIndex(session));
        payload.put("totalQuestions", resolveTotalQuestions(session));

        eventPublisher.pushLiveFeedback(session.getId(), payload);
        log.info("[Processor] LIVE_FEEDBACK pushed for session={} score={}", session.getId(), score);
    }

    private void handleAdvance(InterviewSession session, AnswerEvaluation eval, String transcript) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            session.setStatus(SessionStatus.IN_PROGRESS);
        }

        int currentIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        int nextIndex = currentIndex + 1;
        Optional<SessionQuestionOrder> nextOrderOpt = sqoRepository.findBySessionIdAndQuestionOrder(session.getId(), nextIndex);

        if (nextOrderOpt.isEmpty()) {
            closeSessionWithReport(session, transcript);
            return;
        }

        InterviewQuestion next = nextOrderOpt.get().getQuestion();
        if (next == null) {
            log.error("[Processor] Next question missing for session={} index={}", session.getId(), nextIndex);
            pushFallbackQuestion(session, transcript);
            return;
        }

        session.setCurrentQuestionIndex(nextIndex);
        sessionRepo.save(session);
        pushNextQuestion(session, next, eval, transcript);
    }

    private void pushNextQuestion(InterviewSession session,
                                  InterviewQuestion next,
                                  AnswerEvaluation eval,
                                  String transcript) {
        String speechText;
        boolean isFollowUp = false;
        String followUp = eval != null ? eval.getFollowUpGenerated() : null;
        double score = safeOverallScore(eval);

        if (followUp != null && !followUp.isBlank() && score >= 5.5 && score < 8.5) {
            speechText = responseBuilder.buildFollowUpSpeech(followUp);
            isFollowUp = true;
        } else {
            speechText = responseBuilder.buildTransitionSpeech(next.getQuestionText());
        }

        String audioUrl = synthesizeAndGetUrl(speechText);
        if (audioUrl == null) {
            log.error("[Processor] TTS failed for next question - using fallback question");
            pushFallbackQuestion(session, transcript);
            return;
        }

        LinkedHashMap<String, Object> payload = buildSpeechPayload(
                audioUrl,
                speechText,
                isFollowUp,
                false,
                false,
                next.getId(),
                next.getQuestionText(),
                safeDisplayIndex(session),
                resolveTotalQuestions(session),
                transcript
        );

        log.info("[Processor] Pushing LIVE_AI_SPEECH: url={}", audioUrl);
        eventPublisher.pushLiveAiSpeech(session.getId(), payload);
    }

    private void closeSessionWithReport(InterviewSession session, String transcript) {
        log.info("[Processor] Last question answered - generating report for session={}", session.getId());

        String closingText;
        try {
            closingText = greetingBuilder.buildClosing(session);
        } catch (Exception ex) {
            closingText = "Thank you for your time. We will be in touch.";
        }

        String closingUrl = synthesizeAndGetUrl(closingText);

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepo.save(session);
        log.info("[Processor] Session {} completed", session.getId());

        LinkedHashMap<String, Object> payload = buildSpeechPayload(
                closingUrl,
                closingText,
                false,
                false,
                true,
                null,
                null,
                safeDisplayIndex(session),
                resolveTotalQuestions(session),
                transcript
        );
        eventPublisher.pushLiveAiSpeech(session.getId(), payload);

        audioBuffer.clearSession(session.getId());

        scheduleReportGenerationAfterCommit(session.getId());
    }

    private void scheduleReportGenerationAfterCommit(Long sessionId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    generateReportAsync(sessionId);
                }
            });
            return;
        }

        generateReportAsync(sessionId);
    }

    private void generateReportAsync(Long sessionId) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[Processor] Generating report for session={}", sessionId);
                InterviewReport report = reportService.generateReport(sessionId);
                double finalScore = report.getFinalScore() == null ? 0.0 : report.getFinalScore();
                log.info("[Processor] Report generated: id={} score={}", report.getId(), finalScore);

                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                payload.put("reportId", report.getId());
                payload.put("finalScore", finalScore);
                payload.put("sessionId", sessionId);
                eventPublisher.pushReportReady(sessionId, payload);
            } catch (Exception ex) {
                log.error("[Processor] Report generation failed: {}", ex.getMessage(), ex);

                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                payload.put("reportId", -1L);
                payload.put("finalScore", 0.0);
                payload.put("sessionId", sessionId);
                payload.put("error", "Report generation failed");
                eventPublisher.pushReportReady(sessionId, payload);
            }
        }, liveExecutor);
    }

    private int estimateSeconds(int bytes) {
        return Math.max(1, bytes / 12000);
    }

    private double safeOverallScore(AnswerEvaluation evaluation) {
        if (evaluation == null) {
            return 5.0;
        }

        if (evaluation.getOverallScore() != null) {
            return evaluation.getOverallScore();
        }

        return round2(average(
                evaluation.getContentScore(),
                evaluation.getClarityScore(),
                evaluation.getTechnicalScore()
        ));
    }

    private double average(Double... values) {
        double sum = 0.0;
        int count = 0;
        if (values == null) {
            return 5.0;
        }

        for (Double value : values) {
            if (value != null) {
                sum += value;
                count += 1;
            }
        }

        if (count == 0) {
            return 5.0;
        }

        return sum / count;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void pushFallbackQuestion(InterviewSession session, String transcript) {
        log.warn("[Processor] Pushing fallback question for session {}", session.getId());

        String text = "Let us move on. Could you tell me about a time you overcame a challenge?";
        String audioUrl = synthesizeAndGetUrl(text);
        if (audioUrl == null) {
            log.error("[Processor] Fallback TTS failed for session {}", session.getId());
            return;
        }

        LinkedHashMap<String, Object> payload = buildSpeechPayload(
                audioUrl,
                text,
                false,
                false,
                false,
                null,
                null,
                safeDisplayIndex(session),
                resolveTotalQuestions(session),
                transcript == null ? "" : transcript
        );
        eventPublisher.pushLiveAiSpeech(session.getId(), payload);
    }

    private void publishTranscriptEvent(Long sessionId, Long answerId, String transcript, int bytes, Long questionId) {
        LinkedHashMap<String, Object> transcriptPayload = new LinkedHashMap<>();
        transcriptPayload.put("answerId", answerId);
        transcriptPayload.put("questionId", questionId);
        transcriptPayload.put("transcript", transcript);
        transcriptPayload.put("bytes", bytes);

        eventPublisher.push(sessionId, SessionEvent.builder()
                .eventType("LIVE_TRANSCRIPT")
                .sessionId(sessionId)
                .payload(transcriptPayload)
                .message("Transcript ready")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private int safeDisplayIndex(InterviewSession session) {
        int current = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        return current + 1;
    }

    private int resolveTotalQuestions(InterviewSession session) {
        if (session.getQuestionCountRequested() != null) {
            return session.getQuestionCountRequested();
        }
        return (int) sqoRepository.countBySessionId(session.getId());
    }

    private String writeTempFile(byte[] data, Long sessionId) {
        try {
            Path tmp = Files.createTempFile("live_" + sessionId + "_", ".webm");
            Files.write(tmp, data == null ? new byte[0] : data);
            return tmp.toAbsolutePath().toString();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write temp audio: " + ex.getMessage(), ex);
        }
    }

    private String synthesizeAndGetUrl(String text) {
        try {
            String safe = text == null ? "" : text;
            log.info("[Processor] Synthesizing: \"{}\"", safe.substring(0, Math.min(60, safe.length())));
            String generated = ttsClient.synthesize(safe);
            if (generated == null || generated.isBlank()) {
                return null;
            }

            String filename = Paths.get(generated).getFileName().toString();
            String url = buildAudioUrl(filename);
            log.info("[Processor] TTS OK -> {}", url);
            return url;
        } catch (Exception ex) {
            log.error("[Processor] TTS failed: {}", ex.getMessage());
            return null;
        }
    }

    private LinkedHashMap<String, Object> buildSpeechPayload(String audioUrl,
                                                             String text,
                                                             boolean isFollowUp,
                                                             boolean isRetry,
                                                             boolean isClosing,
                                                             Long nextQuestionId,
                                                             String nextQuestionText,
                                                             Integer currentQuestionIndex,
                                                             Integer totalQuestions,
                                                             String transcript) {
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
        payload.put("transcript", transcript == null ? "" : transcript);
        return payload;
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
}
