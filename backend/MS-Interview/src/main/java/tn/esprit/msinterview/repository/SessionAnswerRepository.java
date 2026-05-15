package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msinterview.entity.SessionAnswer;

import java.util.List;
import java.util.Optional;

public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, Long> {
    List<SessionAnswer> findBySessionId(Long sessionId);
    List<SessionAnswer> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    Optional<SessionAnswer> findTopBySessionIdAndQuestionIdOrderBySubmittedAtDesc(Long sessionId, Long questionId);
    
    List<SessionAnswer> findByParentAnswerId(Long parentAnswerId);
    long countBySessionIdAndQuestionId(Long sessionId, Long questionId);
    List<SessionAnswer> findBySessionIdAndIsFollowUpFalse(Long sessionId);
    boolean existsBySessionIdAndQuestionId(Long sessionId, Long questionId);
}
