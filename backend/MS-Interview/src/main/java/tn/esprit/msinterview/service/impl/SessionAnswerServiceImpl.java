package tn.esprit.msinterview.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.esprit.msinterview.ai.WhisperClient;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.exception.ConflictException;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.AnswerEvaluationService;
import tn.esprit.msinterview.service.AudioAnswerAsyncProcessor;
import tn.esprit.msinterview.service.MLScenarioAnswerService;
import tn.esprit.msinterview.service.SessionAnswerService;
import tn.esprit.msinterview.service.SessionQuestionOrderService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionAnswerServiceImpl implements SessionAnswerService {

    private final SessionAnswerRepository repository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final AnswerEvaluationService evaluationService;
    private final SessionQuestionOrderService questionOrderService;
    private final WhisperClient whisperClient;
    private final AudioStorageService audioStorageService;
    private final MLScenarioAnswerService mlScenarioAnswerService;
    private final AudioAnswerAsyncProcessor audioAnswerAsyncProcessor;

    @Override
    public SessionAnswer submitAnswer(Long sessionId, Long questionId, String answerText, String videoUrl, String audioUrl, String codeAnswer) {
        log.debug("Submitting answer for session {} question {}", sessionId, questionId);

        assertCurrentQuestion(sessionId, questionId);
        SessionAnswer saved = createAnswer(sessionId, questionId, answerText, videoUrl, audioUrl, codeAnswer, false);

        triggerEvaluationAfterCommit(saved.getId());
        triggerMlExtractionAfterCommit(saved.getSession(), saved);
        questionOrderService.advanceToNextQuestion(sessionId);

        log.debug("Answer submitted with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public SessionAnswer submitCodeAnswer(Long sessionId, Long questionId, String code, CodeLanguage language) {
        log.debug("Submitting code answer for session {} question {}", sessionId, questionId);

        assertCurrentQuestion(sessionId, questionId);
        SessionAnswer answer = createAnswer(sessionId, questionId, null, null, null, null, false);
        answer.setCodeAnswer(code);
        answer.setCodeLanguage(language);

        SessionAnswer saved = repository.save(answer);
        triggerEvaluationAfterCommit(saved.getId());
        questionOrderService.advanceToNextQuestion(sessionId);

        log.debug("Code answer saved for language: {}", language);
        return saved;
    }

    @Override
    public SessionAnswer retryAnswer(Long sessionId, Long questionId, String answerText, String videoUrl, String audioUrl, String codeAnswer) {
        log.debug("Retrying answer for session {} question {}", sessionId, questionId);

        Optional<SessionAnswer> latest = repository.findTopBySessionIdAndQuestionIdOrderBySubmittedAtDesc(sessionId, questionId);

        SessionAnswer answer = createAnswer(sessionId, questionId, answerText, videoUrl, audioUrl, codeAnswer, false);
        answer.setRetryCount(latest.map(a -> (a.getRetryCount() == null ? 0 : a.getRetryCount()) + 1).orElse(1));

        SessionAnswer saved = repository.save(answer);
        triggerEvaluationAfterCommit(saved.getId());
        triggerMlExtractionAfterCommit(saved.getSession(), saved);

        log.debug("Answer retry count: {}", answer.getRetryCount());
        return saved;
    }

    @Override
    public SessionAnswer submitFollowUpAnswer(Long sessionId, Long questionId, Long parentAnswerId, String answerText) {
        log.debug("Submitting follow-up answer for session {} question {} parent {}", sessionId, questionId, parentAnswerId);

        SessionAnswer parentAnswer = repository.findById(parentAnswerId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent answer not found: " + parentAnswerId));

        SessionAnswer followUp = createAnswer(sessionId, questionId, answerText, null, null, null, true);
        followUp.setParentAnswer(parentAnswer);
        followUp.setFollowUp(true);

        SessionAnswer saved = repository.save(followUp);
        triggerEvaluationAfterCommit(saved.getId());

        log.debug("Follow-up answer created linked to parent: {}", parentAnswerId);
        return saved;
    }

    @Override
    public List<SessionAnswer> getAnswersBySession(Long sessionId) {
        log.debug("Fetching all answers for session: {}", sessionId);
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        return repository.findBySessionId(sessionId);
    }

    @Override
    public SessionAnswer getAnswerById(Long id) {
        log.debug("Fetching answer by ID: {}", id);
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found: " + id));
    }

    @Override
    public List<SessionAnswer> getPrimaryAnswersBySession(Long sessionId) {
        log.debug("Fetching primary answers for session: {}", sessionId);
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        return repository.findBySessionIdAndIsFollowUpFalse(sessionId);
    }

    private SessionAnswer createAnswer(Long sessionId,
                                       Long questionId,
                                       String answerText,
                                       String videoUrl,
                                       String audioUrl,
                                       String codeAnswer,
                                       boolean isFollowUp) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        InterviewQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));

        SessionAnswer answer = SessionAnswer.builder()
                .session(session)
                .question(question)
                .answerText(answerText)
                .codeAnswer(codeAnswer)
                .videoUrl(videoUrl)
                .audioUrl(audioUrl)
                .retryCount(0)
                .isFollowUp(isFollowUp)
                .submittedAt(LocalDateTime.now())
                .build();

        return repository.save(answer);
    }

    private void assertCurrentQuestion(Long sessionId, Long questionId) {
        Long currentQuestionId = questionOrderService.getCurrentQuestion(sessionId).getId();
        if (!currentQuestionId.equals(questionId)) {
            throw new ConflictException(
                    "Question " + questionId + " is no longer current for session " + sessionId + ". Refresh and answer the active question."
            );
        }
    }

    private void triggerEvaluationAfterCommit(Long answerId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            evaluationService.triggerEvaluation(answerId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evaluationService.triggerEvaluation(answerId);
            }
        });
    }

    private void triggerMlExtractionAfterCommit(InterviewSession session, SessionAnswer answer) {
        if (session.getRoleType() != RoleType.AI || answer.getAnswerText() == null) {
            return;
        }

        Runnable extractionTask = () -> {
            mlScenarioAnswerService.extractAndSaveAsync(answer.getId(), answer.getAnswerText());
            log.info("ML concept extraction triggered for answerId={}", answer.getId());
        };

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            extractionTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                extractionTask.run();
            }
        });
    }

    @Override
    public SessionAnswer submitAudioAnswer(Long sessionId, Long questionId,
                                           MultipartFile audioFile, String videoUrl) {
        log.info("submitAudioAnswer() - session={}, question={}", sessionId, questionId);

        whisperClient.assertCanTranscribeWebm();
        assertCurrentQuestion(sessionId, questionId);

        String audioFilePath;
        try {
            audioFilePath = audioStorageService.saveAudioFile(audioFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save audio file: " + e.getMessage(), e);
        }

        // Save answer quickly so controller can return 202 Accepted immediately.
        SessionAnswer saved = createAnswer(sessionId, questionId, null, videoUrl, audioFilePath, null, false);
        questionOrderService.advanceToNextQuestion(sessionId);

        // Transcription + evaluation runs in background.
        audioAnswerAsyncProcessor.processAudioAnswerAsync(saved.getId(), audioFilePath);
        return saved;
    }

    @Override
    public String transcribeAudio(MultipartFile audioFile) {
        whisperClient.assertCanTranscribeWebm();

        String audioFilePath;
        try {
            audioFilePath = audioStorageService.saveAudioFile(audioFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save audio file: " + e.getMessage(), e);
        }

        String transcript = whisperClient.transcribeFromWebm(audioFilePath);
        return transcript == null ? "" : transcript.trim();
    }
}
