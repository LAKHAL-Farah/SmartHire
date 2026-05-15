package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.AnswerEvaluation;

import java.util.List;

public interface AnswerEvaluationService {
    void triggerEvaluation(Long answerId);
    AnswerEvaluation updateTextScores(Long answerId, Double contentScore, Double clarityScore, Double technicalScore, String aiFeedback, String followUpGenerated);
    AnswerEvaluation updateVoiceScores(Long answerId, Double confidenceScore, Double toneScore, Double emotionScore, Double speechRate, Integer hesitationCount);
    AnswerEvaluation updateVideoScores(Long answerId, Double postureScore, Double eyeContactScore, Double expressionScore);
    AnswerEvaluation updateCodeScores(Long answerId, Double codeCorrectnessScore, String codeComplexityNote);
    AnswerEvaluation getEvaluationByAnswer(Long answerId);
    List<AnswerEvaluation> getEvaluationsBySession(Long sessionId);
    void recalculateOverall(AnswerEvaluation eval);
    boolean isEvaluationComplete(Long answerId);
}
