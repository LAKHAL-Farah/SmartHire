package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.dto.InterviewQuestionDTO;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionQuestionOrder;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionQuestionOrderRepository;
import tn.esprit.msinterview.service.SessionQuestionOrderService;
import tn.esprit.msinterview.websocket.SessionEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionQuestionOrderServiceImpl implements SessionQuestionOrderService {

    private final SessionQuestionOrderRepository repository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final TTSClient ttsClient;
    private final SessionEventPublisher sessionEventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public List<SessionQuestionOrder> getOrderedQuestionsForSession(Long sessionId) {
        log.debug("Fetching ordered questions for session: {}", sessionId);
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        return repository.findBySessionIdOrderByQuestionOrder(sessionId);
    }

    @Override
    public InterviewQuestion getCurrentQuestion(Long sessionId) {
        log.debug("Getting current question for session: {}", sessionId);
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        
        SessionQuestionOrder order = repository.findCurrent(sessionId, session.getCurrentQuestionIndex())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found at index: " + session.getCurrentQuestionIndex()));
        
        return order.getQuestion();
    }

    @Override
    public Optional<InterviewQuestion> advanceToNextQuestion(Long sessionId) {
        log.debug("Advancing to next question for session: {}", sessionId);
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        
        int nextIndex = session.getCurrentQuestionIndex() + 1;
        Optional<SessionQuestionOrder> nextOrder = repository.findBySessionIdAndQuestionOrder(sessionId, nextIndex);
        
        if (nextOrder.isPresent()) {
            session.setCurrentQuestionIndex(nextIndex);
            sessionRepository.save(session);

            InterviewQuestion nextQuestion = nextOrder.get().getQuestion();
            String ttsAudioUrl = ttsClient.preGenerateQuestionAudio(
                    sessionId,
                    nextQuestion.getId(),
                    buildQuestionTtsText(nextQuestion)
            );

            InterviewQuestionDTO payload = DTOMapper.toQuestionDTO(nextQuestion);
            payload.setTtsAudioUrl(ttsAudioUrl);
            sessionEventPublisher.pushNextQuestion(sessionId, payload);

            log.debug("Advanced session {} to question index {}", sessionId, nextIndex);
            log.debug("Next question TTS pre-generated for session {}: {}", sessionId, ttsAudioUrl);
            return Optional.of(nextQuestion);
        }

        // Move pointer beyond last question so callers can detect completion state.
        session.setCurrentQuestionIndex(nextIndex);
        sessionRepository.save(session);
        
        log.debug("No more questions available for session {}", sessionId);
        return Optional.empty();
    }

    @Override
    public void skipCurrentQuestion(Long sessionId) {
        log.debug("Skipping current question for session: {}", sessionId);
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        
        SessionQuestionOrder current = repository.findCurrent(sessionId, session.getCurrentQuestionIndex())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found at index: " + session.getCurrentQuestionIndex()));
        
        repository.markAsSkipped(current.getId());
        advanceToNextQuestion(sessionId);
        log.debug("Skipped question at index {} for session {}", session.getCurrentQuestionIndex(), sessionId);
    }

    @Override
    public void overrideNextQuestion(Long sessionId, Long nextQuestionId) {
        log.debug("Overriding next question for session {} with question {}", sessionId, nextQuestionId);
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        
        InterviewQuestion question = questionRepository.findById(nextQuestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + nextQuestionId));
        
        int nextIndex = session.getCurrentQuestionIndex() + 1;
        Optional<SessionQuestionOrder> nextOrder = repository.findBySessionIdAndQuestionOrder(sessionId, nextIndex);
        
        if (nextOrder.isPresent()) {
            SessionQuestionOrder order = nextOrder.get();
            order.setQuestion(question);
            repository.save(order);
            log.debug("Overrode next question for session {}", sessionId);
        }
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
