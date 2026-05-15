package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.msinterview.entity.InterviewReport;

import java.util.List;
import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {
    Optional<InterviewReport> findBySessionId(Long sessionId);
    
    List<InterviewReport> findByUserIdOrderByGeneratedAtDesc(Long userId);
    
    @Query("SELECT AVG(r.finalScore) FROM InterviewReport r WHERE r.session.careerPathId=:cpId")
    Double findAvgByCareerPath(@Param("cpId") Long careerPathId);
    
    @Query("SELECT COUNT(r) FROM InterviewReport r WHERE r.session.careerPathId=:cpId AND r.finalScore < :score")
    long countBelowScore(@Param("cpId") Long cpId, @Param("score") Double score);
    
    long countBySessionCareerPathId(Long careerPathId);
    
    boolean existsBySessionId(Long sessionId);
}
