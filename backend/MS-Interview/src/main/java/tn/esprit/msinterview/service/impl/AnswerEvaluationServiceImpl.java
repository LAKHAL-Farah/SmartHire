package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.repository.AnswerEvaluationRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.AnswerEvaluationService;
import tn.esprit.msinterview.service.EvaluationEngine;
import tn.esprit.msinterview.service.StressAggregatorService;
import tn.esprit.msinterview.websocket.SessionEventPublisher;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AnswerEvaluationServiceImpl implements AnswerEvaluationService {

    private final AnswerEvaluationRepository repository;
    private final SessionAnswerRepository answerRepository;
    private final EvaluationEngine evaluationEngine;
    private final SessionEventPublisher sessionEventPublisher;
    private final StressAggregatorService stressAggregator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Async
    public void triggerEvaluation(Long answerId) {
        log.debug("Triggering evaluation for answer: {}", answerId);
        SessionAnswer answer = waitForAnswer(answerId);
        if (answer == null) {
            log.error("Answer {} not found after waiting for async commit", answerId);
            return;
        }

        Long sessionId = answer.getSession() != null ? answer.getSession().getId() : null;

        AnswerEvaluation eval = repository.findByAnswerId(answerId).orElse(null);
        if (eval != null && eval.getOverallScore() != null) {
            log.debug("Evaluation already completed for answer {}, skipping regeneration", answerId);
            return;
        }

        if (eval == null) {
            eval = AnswerEvaluation.builder()
                    .answer(answer)
                    .aiFeedback("Evaluation in progress...")
                    .build();
            eval = repository.save(eval);
        }

        try {
            AnswerEvaluation generated = hasCode(answer)
                    ? evaluationEngine.evaluateCodingAnswer(answerId)
                    : evaluationEngine.evaluateTextAnswer(answer);

            eval.setContentScore(generated.getContentScore());
            eval.setClarityScore(generated.getClarityScore());
            eval.setTechnicalScore(generated.getTechnicalScore());
            eval.setCodeCorrectnessScore(generated.getCodeCorrectnessScore());
            eval.setCodeComplexityNote(generated.getCodeComplexityNote());
            eval.setConfidenceScore(generated.getConfidenceScore());
            eval.setToneScore(generated.getToneScore());
            eval.setEmotionScore(generated.getEmotionScore());
            eval.setSpeechRate(generated.getSpeechRate());
            eval.setHesitationCount(generated.getHesitationCount());
            eval.setPostureScore(generated.getPostureScore());
            eval.setEyeContactScore(generated.getEyeContactScore());
            eval.setExpressionScore(generated.getExpressionScore());
            eval.setAiFeedback(generated.getAiFeedback());
            eval.setFollowUpGenerated(generated.getFollowUpGenerated());

            recalculateOverall(eval);
            applyStressSummary(eval, answer);
            repository.save(eval);
            sessionEventPublisher.pushEvaluationReady(sessionId, DTOMapper.toEvaluationDTO(eval));
            log.debug("Evaluation completed for answer {}", answerId);
        } catch (Exception ex) {
            log.error("Evaluation failed for answer {}: {}", answerId, ex.getMessage());

            // Graceful fallback: keep interview flow moving even if upstream AI is unavailable.
            applyFallbackScores(eval, answer, ex.getMessage());
            recalculateOverall(eval);
            applyStressSummary(eval, answer);
            repository.save(eval);
            sessionEventPublisher.pushEvaluationReady(sessionId, DTOMapper.toEvaluationDTO(eval));
        }

        log.debug("Evaluation triggered asynchronously for answer {}", answerId);
    }

    @Override
    public AnswerEvaluation updateTextScores(Long answerId, Double contentScore, Double clarityScore, Double technicalScore, String aiFeedback, String followUpGenerated) {
        log.debug("Updating text scores for answer: {}", answerId);
        
        AnswerEvaluation eval = repository.findByAnswerId(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation not found for answer: " + answerId));
        
        eval.setContentScore(contentScore);
        eval.setClarityScore(clarityScore);
        eval.setTechnicalScore(technicalScore);
        eval.setAiFeedback(aiFeedback);
        eval.setFollowUpGenerated(followUpGenerated);
        
        recalculateOverall(eval);
        return repository.save(eval);
    }

    @Override
    public AnswerEvaluation updateVoiceScores(Long answerId, Double confidenceScore, Double toneScore, Double emotionScore, Double speechRate, Integer hesitationCount) {
        log.debug("Updating voice scores for answer: {}", answerId);
        
        AnswerEvaluation eval = repository.findByAnswerId(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation not found for answer: " + answerId));
        
        eval.setConfidenceScore(confidenceScore);
        eval.setToneScore(toneScore);
        eval.setEmotionScore(emotionScore);
        eval.setSpeechRate(speechRate);
        eval.setHesitationCount(hesitationCount);
        
        recalculateOverall(eval);
        return repository.save(eval);
    }

    @Override
    public AnswerEvaluation updateVideoScores(Long answerId, Double postureScore, Double eyeContactScore, Double expressionScore) {
        log.debug("Updating video scores for answer: {}", answerId);
        
        AnswerEvaluation eval = repository.findByAnswerId(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation not found for answer: " + answerId));
        
        eval.setPostureScore(postureScore);
        eval.setEyeContactScore(eyeContactScore);
        eval.setExpressionScore(expressionScore);
        
        recalculateOverall(eval);
        return repository.save(eval);
    }

    @Override
    public AnswerEvaluation updateCodeScores(Long answerId, Double codeCorrectnessScore, String codeComplexityNote) {
        log.debug("Updating code scores for answer: {}", answerId);
        
        AnswerEvaluation eval = repository.findByAnswerId(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation not found for answer: " + answerId));
        
        eval.setCodeCorrectnessScore(codeCorrectnessScore);
        eval.setCodeComplexityNote(codeComplexityNote);
        
        recalculateOverall(eval);
        return repository.save(eval);
    }

    @Override
    public AnswerEvaluation getEvaluationByAnswer(Long answerId) {
        log.debug("Fetching evaluation for answer: {}", answerId);
        return repository.findByAnswerId(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation not found for answer: " + answerId));
    }

    @Override
    public List<AnswerEvaluation> getEvaluationsBySession(Long sessionId) {
        log.debug("Fetching evaluations for session: {}", sessionId);
        return repository.findByAnswerSessionId(sessionId);
    }

    @Override
    public void recalculateOverall(AnswerEvaluation eval) {
        log.debug("Recalculating overall score for evaluation: {}", eval.getId());

        Double contentOnly = eval.getContentScore();
        if (contentOnly == null) {
            contentOnly = eval.getClarityScore();
        }
        if (contentOnly == null) {
            contentOnly = eval.getTechnicalScore();
        }
        if (contentOnly == null) {
            contentOnly = eval.getCodeCorrectnessScore();
        }

        double normalized = contentOnly == null ? 0.0 : contentOnly;
        normalized = Math.max(0.0, Math.min(10.0, normalized));

        eval.setOverallScore(round2(normalized));

        log.debug("Recalculated overall score (content-only mode): {}", normalized);
    }

    @Override
    public boolean isEvaluationComplete(Long answerId) {
        log.debug("Checking if evaluation is complete for answer: {}", answerId);
        return repository.findByAnswerId(answerId)
                .map(eval -> eval.getOverallScore() != null)
                .orElse(false);
    }

    private static Double average(Double a, Double b) {
        if (a == null && b == null) return 0.0;
        if (a == null) return b;
        if (b == null) return a;
        return (a + b) / 2.0;
    }

    private static Double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private SessionAnswer waitForAnswer(Long answerId) {
        final int maxAttempts = 150;
        final long sleepMs = 200;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            SessionAnswer answer = answerRepository.findById(answerId).orElse(null);
            if (answer != null) {
                return answer;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        log.warn("Answer {} not visible for evaluation after {} ms", answerId, maxAttempts * sleepMs);
        return null;
    }

    private boolean hasCode(SessionAnswer answer) {
        return answer != null
                && answer.getCodeAnswer() != null
                && !answer.getCodeAnswer().isBlank();
    }

    private void applyFallbackScores(AnswerEvaluation eval, SessionAnswer answer, String reason) {
        String answerText = answer != null && answer.getAnswerText() != null
                ? answer.getAnswerText().trim()
                : "";
        int words = answerText.isBlank() ? 0 : answerText.split("\\s+").length;

        double contentScore;
        double clarityScore;
        double technicalScore;

        if (words >= 60) {
            contentScore = 7.2;
            clarityScore = 7.0;
            technicalScore = 6.8;
        } else if (words >= 30) {
            contentScore = 6.4;
            clarityScore = 6.2;
            technicalScore = 6.0;
        } else if (words >= 12) {
            contentScore = 5.6;
            clarityScore = 5.4;
            technicalScore = 5.2;
        } else {
            contentScore = 4.5;
            clarityScore = 4.4;
            technicalScore = 4.2;
        }

        eval.setContentScore(round2(contentScore));
        eval.setClarityScore(round2(clarityScore));
        eval.setTechnicalScore(round2(technicalScore));
        eval.setConfidenceScore(round2(clarityScore));
        eval.setToneScore(round2(clarityScore));
        eval.setPostureScore(round2(clarityScore));
        if (eval.getFollowUpGenerated() == null || eval.getFollowUpGenerated().isBlank()) {
            eval.setFollowUpGenerated("Can you explain your trade-offs and security considerations in more detail?");
        }
        eval.setAiFeedback("Automatic fallback evaluation was used because AI scoring was temporarily unavailable. "
                + "Your answer was still scored so the interview can continue. "
                + "Please retry later for a full AI assessment."
                + (reason == null || reason.isBlank() ? "" : " (Reason: " + reason + ")"));
    }

    private void applyStressSummary(AnswerEvaluation eval, SessionAnswer answer) {
        if (eval == null || answer == null || answer.getSession() == null || answer.getQuestion() == null) {
            return;
        }

        Long sessionId = answer.getSession().getId();
        Long questionId = answer.getQuestion().getId();

        stressAggregator.getFinalizedQuestionSummary(sessionId, questionId).ifPresent(summary -> {
            eval.setAvgStressScore(summary.avgScore());
            eval.setStressPeakLevel(summary.level());
            eval.setStressReadingCount(summary.readingCount());

            try {
                eval.setStressTimeline(objectMapper.writeValueAsString(summary.timeline()));
            } catch (Exception ignored) {
                eval.setStressTimeline("[]");
            }
        });
    }
}
