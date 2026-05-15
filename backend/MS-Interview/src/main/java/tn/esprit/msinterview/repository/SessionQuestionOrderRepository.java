package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import tn.esprit.msinterview.entity.SessionQuestionOrder;

import java.util.List;
import java.util.Optional;

public interface SessionQuestionOrderRepository extends JpaRepository<SessionQuestionOrder, Long> {
    List<SessionQuestionOrder> findBySessionIdOrderByQuestionOrder(Long sessionId);
    Optional<SessionQuestionOrder> findBySessionIdAndQuestionOrder(Long sessionId, Integer order);
    
    @Query("SELECT spo FROM SessionQuestionOrder spo WHERE spo.session.id=:sid AND spo.questionOrder=:idx")
    Optional<SessionQuestionOrder> findCurrent(@Param("sid") Long sessionId, @Param("idx") Integer currentIndex);
    
    @Modifying
    @Query("UPDATE SessionQuestionOrder spo SET spo.wasSkipped=true WHERE spo.id=:id")
    void markAsSkipped(@Param("id") Long id);
    
    long countBySessionId(Long sessionId);
    void deleteBySessionId(Long sessionId);
}
