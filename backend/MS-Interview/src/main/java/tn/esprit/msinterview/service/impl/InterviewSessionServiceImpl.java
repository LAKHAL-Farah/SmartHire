package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.LiveGreetingBuilder;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionQuestionOrder;
import tn.esprit.msinterview.dto.LiveSessionStartRequest;
import tn.esprit.msinterview.dto.LiveSessionStartResponse;
import tn.esprit.msinterview.entity.enumerated.InterviewMode;
import tn.esprit.msinterview.entity.enumerated.InterviewType;
import tn.esprit.msinterview.entity.enumerated.LiveSubMode;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionQuestionOrderRepository;
import tn.esprit.msinterview.service.InterviewStreakService;
import tn.esprit.msinterview.service.InterviewSessionService;
import tn.esprit.msinterview.service.SessionQuestionOrderService;
import tn.esprit.msinterview.websocket.LiveAudioBuffer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InterviewSessionServiceImpl implements InterviewSessionService {


    

    private final InterviewSessionRepository repository;
    private final InterviewQuestionRepository questionRepository;
    private final SessionQuestionOrderRepository questionOrderRepository;
    private final InterviewStreakService streakService;
    private final TTSClient ttsClient;
    private final LiveGreetingBuilder liveGreetingBuilder;
    private final SessionQuestionOrderService sqoService;
    private final ObjectMapper objectMapper;
    private final LiveAudioBuffer liveAudioBuffer;

    @Value("${smarthire.audio.base-url:/interview-service/api/v1/audio}")
    private String audioBaseUrl;

    // Temporary startup check for Step 1 verification. Remove after manual validation is complete.
    @PostConstruct
    public void ttsSmokeTest() {
        log.info("[TTSSmokeTest] Starting TTS smoke test...");
        try {
            String filename = ttsClient.synthesize("Hello. This is a TTS smoke test.");
            if (filename == null || filename.isBlank()) {
                log.error("[TTSSmokeTest] FAILED - synthesize returned null/blank filename");
                return;
            }

            Path path = ttsClient.resolveAudioFilePath(filename);
            File file = path.toFile();
            if (!file.exists() || file.length() == 0) {
                log.error("[TTSSmokeTest] FAILED - file does not exist or is empty: {}", path);
            } else {
                log.info("[TTSSmokeTest] PASSED - file exists: {} ({} bytes)", path, file.length());
            }
        } catch (Exception e) {
            log.error("[TTSSmokeTest] FAILED - exception: {}", e.getMessage(), e);
        }
    }

    @Override
    public InterviewSession startSession(Long userId,
                                         Long careerPathId,
                                         RoleType role,
                                         InterviewMode mode,
                                         InterviewType type,
                                         Integer questionCount) {
        log.debug("Starting new interview session for userId: {}, careerPathId: {}", userId, careerPathId);

        int safeQuestionCount = questionCount != null ? questionCount : 5;
        LiveSubMode liveSubMode = mode == InterviewMode.TEST ? LiveSubMode.TEST_LIVE : LiveSubMode.PRACTICE_LIVE;


            
        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .careerPathId(careerPathId)
                .roleType(role)
                .mode(mode)
                .type(type)
                .status(SessionStatus.IN_PROGRESS)
                .currentQuestionIndex(0)
                .durationSeconds(0)
                .startedAt(LocalDateTime.now())
                .isPressureMode(false)
                .pressureEventsTriggered(0)
                .liveSubMode(liveSubMode)
                .questionCountRequested(safeQuestionCount)
                .retryCountTotal(0)
                .build();

        InterviewSession saved = repository.save(session);

        List<InterviewQuestion> candidates = new ArrayList<>(
                questionRepository.findByCareerPathIdAndIsActiveTrue(careerPathId)
        );
        candidates.removeIf(q -> q.getRoleType() != role && q.getRoleType() != RoleType.ALL);
        candidates = orderCandidatesForSession(candidates, role, type);

        if (candidates.isEmpty()) {
            candidates = questionRepository.findByRoleTypeInAndIsActiveTrue(List.of(role, RoleType.ALL));
            candidates = orderCandidatesForSession(candidates, role, type);
        }

        int limit = Math.min(Math.max(safeQuestionCount, 0), candidates.size());

        List<InterviewQuestion> selectedQuestions = selectQuestionsForSession(candidates, role, type, limit);
        ensureCloudCanvasFirstQuestion(selectedQuestions, candidates, role, type);

        for (int i = 0; i < selectedQuestions.size(); i++) {
            SessionQuestionOrder order = SessionQuestionOrder.builder()
                    .session(saved)
                    .question(selectedQuestions.get(i))
                    .questionOrder(i)
                    .timeAllottedSeconds(120)
                    .wasSkipped(false)
                    .build();
            questionOrderRepository.save(order);
        }

        if (!selectedQuestions.isEmpty()) {
            InterviewQuestion firstQuestion = selectedQuestions.get(0);
            String ttsAudioUrl = ttsClient.preGenerateQuestionAudio(
                    saved.getId(),
                    firstQuestion.getId(),
                    buildQuestionTtsText(firstQuestion)
            );
            log.debug("First question TTS pre-generated for session {}: {}", saved.getId(), ttsAudioUrl);
        }

        log.info("Interview session created with id: {}", saved.getId());



        return saved;
    }

        @Override
        public LiveSessionStartResponse startLiveSession(LiveSessionStartRequest request) {
        log.info("[LiveSession] startLiveSession: userId={} questions={} subMode={}",
            request.userId(), request.questionCount(), request.liveSubMode());

        Optional<InterviewSession> existing = repository.findActiveSession(request.userId());
        if (existing.isPresent()) {
                InterviewSession previous = existing.get();
                previous.setStatus(SessionStatus.ABANDONED);
                previous.setEndedAt(LocalDateTime.now());
                repository.save(previous);
                log.info("[LiveSession] Auto-closed previous active session {} for user {}",
                        previous.getId(), request.userId());
        }

        InterviewSession session = InterviewSession.builder()
            .userId(request.userId())
            .careerPathId(request.careerPathId())
            .roleType(RoleType.ALL)
            .mode(InterviewMode.LIVE)
            .liveSubMode(request.liveSubMode())
            .liveMode(true)
            .type(InterviewType.BEHAVIORAL)
            .status(SessionStatus.IN_PROGRESS)
            .questionCountRequested(request.questionCount())
            .silenceThresholdMs(4500)
            .currentQuestionIndex(0)
            .startedAt(LocalDateTime.now())
            .durationSeconds(0)
            .isPressureMode(false)
            .pressureEventsTriggered(0)
            .retryCountTotal(0)
            .build();

        session = repository.save(session);
        log.info("[LiveSession] Session created: id={}", session.getId());

        List<InterviewQuestion> questions = new ArrayList<>(
            questionRepository.findByTypeAndIsActiveTrue(QuestionType.BEHAVIORAL)
        );
        if (questions.size() < request.questionCount()) {
            log.error("[LiveSession] Not enough behavioral questions: found={} needed={}",
                questions.size(), request.questionCount());
            throw new RuntimeException("Not enough behavioral questions in the bank. Found: " + questions.size());
        }

        Collections.shuffle(questions);
        List<InterviewQuestion> selected = questions.subList(0, request.questionCount());

        for (int i = 0; i < selected.size(); i++) {
            SessionQuestionOrder order = SessionQuestionOrder.builder()
                .session(session)
                .question(selected.get(i))
                .questionOrder(i)
                .timeAllottedSeconds(120)
                .wasSkipped(false)
                .build();
            questionOrderRepository.save(order);
        }
        log.info("[LiveSession] {} questions assigned to session {}", selected.size(), session.getId());

        InterviewQuestion firstQuestion = selected.get(0);
        String greetingText = liveGreetingBuilder.buildGreeting(
            session,
            request.companyName(),
            request.targetRole(),
            firstQuestion
        );

        log.info("[LiveSession] Greeting text ({} chars): \"{}\"",
            greetingText.length(),
            greetingText.substring(0, Math.min(80, greetingText.length())) + "...");

        String greetingAudioUrl = "";
        String greetingPath = ttsClient.synthesize(greetingText);
        if (greetingPath == null || greetingPath.isBlank()) {
            log.warn("[LiveSession] Greeting TTS generation failed for session {}. Continuing without greeting audio.",
                session.getId());
        } else {
            String greetingFilename = Paths.get(greetingPath).getFileName().toString();
            String normalizedBase = audioBaseUrl == null || audioBaseUrl.isBlank()
                ? "/interview-service/api/v1/audio"
                : audioBaseUrl.trim();
            if (normalizedBase.endsWith("/")) {
                normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
            }
            greetingAudioUrl = normalizedBase + "/" + greetingFilename;
            log.info("[LiveSession] Greeting audio: {}", greetingAudioUrl);
        }

        return LiveSessionStartResponse.builder()
            .sessionId(session.getId())
            .greetingAudioUrl(greetingAudioUrl)
            .firstQuestionText(firstQuestion.getQuestionText())
            .firstQuestionId(firstQuestion.getId())
            .totalQuestions(request.questionCount())
            .liveSubMode(request.liveSubMode().name())
            .status(session.getStatus().name())
            .build();
        }

    private List<InterviewQuestion> orderCandidatesForSession(List<InterviewQuestion> candidates,
                                                              RoleType role,
                                                              InterviewType interviewType) {
        return candidates.stream()
                .sorted(
                        Comparator.comparingInt((InterviewQuestion question) -> priorityFor(question, role, interviewType))
                                .thenComparing(InterviewQuestion::getId)
                )
                .toList();
    }

    private int priorityFor(InterviewQuestion question, RoleType sessionRole, InterviewType interviewType) {
        boolean roleExact = question.getRoleType() == sessionRole;
        boolean roleAll = question.getRoleType() == RoleType.ALL;
        QuestionType questionType = question.getType();

        if (interviewType == InterviewType.TECHNICAL) {
            if (roleExact && questionType == QuestionType.CODING) {
                return 0;
            }
            if (roleExact && questionType == QuestionType.TECHNICAL) {
                return 1;
            }
            if (roleAll && questionType == QuestionType.CODING) {
                return 2;
            }
            if (roleAll && questionType == QuestionType.TECHNICAL) {
                return 3;
            }
            if (roleExact) {
                return 4;
            }
            if (roleAll) {
                return 5;
            }
            return 6;
        }

        if (interviewType == InterviewType.BEHAVIORAL) {
            if (roleExact && questionType == QuestionType.BEHAVIORAL) {
                return 0;
            }
            if (roleAll && questionType == QuestionType.BEHAVIORAL) {
                return 1;
            }
            if (roleExact) {
                return 2;
            }
            if (roleAll) {
                return 3;
            }
            return 4;
        }

        if (roleExact && questionType == QuestionType.CODING) {
            return 0;
        }
        if (roleExact && questionType == QuestionType.TECHNICAL) {
            return 1;
        }
        if (roleExact && questionType == QuestionType.BEHAVIORAL) {
            return 2;
        }
        if (roleAll && questionType == QuestionType.CODING) {
            return 3;
        }
        if (roleAll && questionType == QuestionType.TECHNICAL) {
            return 4;
        }
        if (roleAll && questionType == QuestionType.BEHAVIORAL) {
            return 5;
        }

        return 6;
    }

    private List<InterviewQuestion> selectQuestionsForSession(List<InterviewQuestion> orderedCandidates,
                                                              RoleType role,
                                                              InterviewType interviewType,
                                                              int limit) {
        if (limit <= 0 || orderedCandidates.isEmpty()) {
            return new ArrayList<>();
        }

        if (role != RoleType.AI || interviewType != InterviewType.TECHNICAL) {
            return new ArrayList<>(orderedCandidates.subList(0, limit));
        }

        List<InterviewQuestion> technicalCandidates = orderedCandidates.stream()
                .filter(question -> question.getType() == QuestionType.TECHNICAL)
                .toList();

        if (technicalCandidates.isEmpty()) {
            return new ArrayList<>(orderedCandidates.subList(0, limit));
        }

        List<InterviewQuestion> mlPipeline = technicalCandidates.stream()
                .filter(this::isMlPipelineQuestion)
                .toList();

        List<InterviewQuestion> standardAiTechnical = technicalCandidates.stream()
                .filter(question -> !isMlPipelineQuestion(question))
                .toList();

        if (mlPipeline.isEmpty() || standardAiTechnical.isEmpty() || limit < 2) {
            return new ArrayList<>(orderedCandidates.subList(0, limit));
        }

        List<InterviewQuestion> mixed = new ArrayList<>(limit);
        Set<Long> selectedIds = new HashSet<>();

        int standardIndex = 0;
        int pipelineIndex = 0;

        while (mixed.size() < limit && (standardIndex < standardAiTechnical.size() || pipelineIndex < mlPipeline.size())) {
            if (standardIndex < standardAiTechnical.size()) {
                InterviewQuestion question = standardAiTechnical.get(standardIndex++);
                if (selectedIds.add(question.getId())) {
                    mixed.add(question);
                }
            }

            if (mixed.size() >= limit) {
                break;
            }

            if (pipelineIndex < mlPipeline.size()) {
                InterviewQuestion question = mlPipeline.get(pipelineIndex++);
                if (selectedIds.add(question.getId())) {
                    mixed.add(question);
                }
            }
        }

        if (mixed.size() < limit) {
            for (InterviewQuestion question : orderedCandidates) {
                if (mixed.size() >= limit) {
                    break;
                }
                if (selectedIds.add(question.getId())) {
                    mixed.add(question);
                }
            }
        }

        return mixed;
    }

    private void ensureCloudCanvasFirstQuestion(List<InterviewQuestion> selectedQuestions,
                                                List<InterviewQuestion> orderedCandidates,
                                                RoleType role,
                                                InterviewType type) {
        if (!isCloudTechnicalSession(role, type) || selectedQuestions.isEmpty()) {
            return;
        }

        int canvasIndexInSelection = -1;
        for (int i = 0; i < selectedQuestions.size(); i++) {
            if (isCanvasQuestion(selectedQuestions.get(i))) {
                canvasIndexInSelection = i;
                break;
            }
        }

        if (canvasIndexInSelection == 0) {
            return;
        }

        if (canvasIndexInSelection > 0) {
            Collections.swap(selectedQuestions, 0, canvasIndexInSelection);
            return;
        }

        Optional<InterviewQuestion> canvasFallback = orderedCandidates.stream()
                .filter(this::isCanvasQuestion)
                .findFirst();

        if (canvasFallback.isPresent()) {
            selectedQuestions.set(0, canvasFallback.get());
        }
    }

    private boolean isCloudTechnicalSession(RoleType role, InterviewType type) {
        if (type != InterviewType.TECHNICAL) {
            return false;
        }

        return role == RoleType.CLOUD;
    }

    private boolean isCanvasQuestion(InterviewQuestion question) {
        if (question == null) {
            return false;
        }

        if (question.getMetadata() == null || question.getMetadata().isBlank()) {
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(question.getMetadata());
            return "canvas".equalsIgnoreCase(root.path("mode").asText(""));
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isMlPipelineQuestion(InterviewQuestion question) {
        return question != null
                && question.getDomain() != null
                && "ml_pipeline".equalsIgnoreCase(question.getDomain());
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewSession getSessionById(Long id) {
        log.debug("Fetching interview session by id: {}", id);
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewSession> getSessionsByUser(Long userId) {
        log.debug("Fetching sessions for userId: {}", userId);
        return repository.findAllByUserIdFromSessionTable(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewSession> getSessionsByUserAndStatus(Long userId, SessionStatus status) {
        log.debug("Fetching sessions for userId: {} with status: {}", userId, status);
        return repository.findAllByUserIdAndStatusFromSessionTable(userId, status.name());
    }

    @Override
    public InterviewSession pauseSession(Long id) {
        log.debug("Pausing session: {}", id);
        InterviewSession session = getSessionById(id);

        if (session.getMode() != InterviewMode.PRACTICE) {
            throw new IllegalStateException("Only PRACTICE mode sessions can be paused");
        }

        session.setStatus(SessionStatus.PAUSED);
        InterviewSession updated = repository.save(session);
        log.info("Session paused: {}", id);
        return updated;
    }

    @Override
    public InterviewSession resumeSession(Long id) {
        log.debug("Resuming session: {}", id);
        InterviewSession session = getSessionById(id);

        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new IllegalStateException("Session must be in PAUSED status to resume");
        }

        session.setStatus(SessionStatus.IN_PROGRESS);
        InterviewSession updated = repository.save(session);
        log.info("Session resumed: {}", id);
        return updated;
    }

    @Override
    public InterviewSession completeSession(Long id) {
        log.debug("Completing session: {}", id);
        InterviewSession session = getSessionById(id);

        if (session.getStatus() == SessionStatus.COMPLETED) {
            log.info("Session already completed: {}", id);
            return session;
        }

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        InterviewSession updated = repository.save(session);
        streakService.updateAfterSession(session.getUserId());
        log.info("Session completed: {}", id);
        return updated;
    }

    @Override
    public InterviewSession abandonSession(Long id) {
        log.debug("Abandoning session: {}", id);
        InterviewSession session = getSessionById(id);

        session.setStatus(SessionStatus.ABANDONED);
        session.setEndedAt(LocalDateTime.now());
        InterviewSession updated = repository.save(session);
        liveAudioBuffer.clearSession(id);
        log.info("Session abandoned: {}", id);
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewSession> getActiveSession(Long userId) {
        log.debug("Fetching active session for userId: {}", userId);
        return repository.findActiveSession(userId);
    }

    @Override
    public void incrementPressureEventCount(Long sessionId) {
        log.debug("Incrementing pressure event count for session: {}", sessionId);
        InterviewSession session = getSessionById(sessionId);
        session.setPressureEventsTriggered(session.getPressureEventsTriggered() + 1);
        repository.save(session);
    }

    private String buildQuestionTtsText(InterviewQuestion question) {
        if (question == null) {
            return "";
        }

        String fallback = question.getQuestionText();
        String metadata = question.getMetadata();
        if (metadata == null || metadata.isBlank()) {
            return fallback;
        }

        try {
            JsonNode root = objectMapper.readTree(metadata);
            String mode = root.path("mode").asText("").trim();
            if (!"canvas".equalsIgnoreCase(mode)) {
                return fallback;
            }

            String scenario = root.path("scenario").asText("").trim();
            if (scenario.isBlank()) {
                return fallback;
            }

            return firstNSentences(scenario, 3);
        } catch (Exception ex) {
            log.debug("Failed to parse metadata for question {} while preparing TTS: {}", question.getId(), ex.getMessage());
            return fallback;
        }
    }

    private String firstNSentences(String text, int n) {
        if (text == null || text.isBlank() || n <= 0) {
            return "";
        }

        String[] sentences = text.trim().split("(?<=[.!?])\\s+");
        if (sentences.length == 0) {
            return text.trim();
        }

        int count = Math.min(n, sentences.length);
        return String.join(" ", Arrays.copyOf(sentences, count));
    }



}
