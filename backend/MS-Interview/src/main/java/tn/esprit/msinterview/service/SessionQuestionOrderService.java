package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.SessionQuestionOrder;

import java.util.List;
import java.util.Optional;

public interface SessionQuestionOrderService {
    List<SessionQuestionOrder> getOrderedQuestionsForSession(Long sessionId);
    InterviewQuestion getCurrentQuestion(Long sessionId);
    Optional<InterviewQuestion> advanceToNextQuestion(Long sessionId);
    void skipCurrentQuestion(Long sessionId);
    void overrideNextQuestion(Long sessionId, Long nextQuestionId);
}
