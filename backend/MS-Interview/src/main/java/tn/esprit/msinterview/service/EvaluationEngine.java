package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.SessionAnswer;

public interface EvaluationEngine {
    AnswerEvaluation evaluateTextAnswer(SessionAnswer answer);
    AnswerEvaluation evaluateCodingAnswer(Long answerId);
}
