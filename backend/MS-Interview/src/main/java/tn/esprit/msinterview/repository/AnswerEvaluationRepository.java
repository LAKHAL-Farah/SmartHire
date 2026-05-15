package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.msinterview.entity.AnswerEvaluation;

import java.util.List;
import java.util.Optional;

public interface AnswerEvaluationRepository extends JpaRepository<AnswerEvaluation, Long> {
    Optional<AnswerEvaluation> findByAnswerId(Long answerId);
    List<AnswerEvaluation> findByAnswerSessionId(Long sessionId);
    List<AnswerEvaluation> findByAnswerSessionIdOrderByOverallScoreAsc(Long sessionId);
    
    @Query("SELECT AVG(ae.overallScore) FROM AnswerEvaluation ae WHERE ae.answer.session.careerPathId=:cpId AND ae.answer.session.status='COMPLETED'")
    Double findAverageScoreByCareerPathId(@Param("cpId") Long careerPathId);
    
    boolean existsByAnswerIdAndOverallScoreIsNotNull(Long answerId);
    
    @Query("SELECT COUNT(ae) FROM AnswerEvaluation ae WHERE ae.answer.session.id=:sid AND ae.overallScore IS NOT NULL")
    long countCompletedEvals(@Param("sid") Long sessionId);
}
