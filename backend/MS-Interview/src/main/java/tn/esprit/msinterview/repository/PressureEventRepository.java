package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.msinterview.entity.PressureEvent;
import tn.esprit.msinterview.entity.enumerated.PressureEventType;

import java.util.List;

public interface PressureEventRepository extends JpaRepository<PressureEvent, Long> {
    List<PressureEvent> findBySessionIdOrderByTriggeredAtAsc(Long sessionId);
    
    long countBySessionId(Long sessionId);
    
    List<PressureEvent> findBySessionIdAndEventType(Long sessionId, PressureEventType type);
    
    long countBySessionIdAndCandidateReacted(Long sessionId, boolean reacted);
    
    @Query("SELECT AVG(p.reactionTimeMs) FROM PressureEvent p WHERE p.session.id=:sid AND p.candidateReacted=true")
    Double findAvgReactionTime(@Param("sid") Long sessionId);
}
