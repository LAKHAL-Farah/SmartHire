package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.msinterview.entity.CodeExecutionResult;

import java.util.List;
import java.util.Optional;

public interface CodeExecutionResultRepository extends JpaRepository<CodeExecutionResult, Long> {
    List<CodeExecutionResult> findByAnswerId(Long answerId);
    
    @Query("SELECT c FROM CodeExecutionResult c WHERE c.answer.id=:aid ORDER BY c.id DESC")
    Optional<CodeExecutionResult> findLatest(@Param("aid") Long answerId);
    
    List<CodeExecutionResult> findByAnswerSessionId(Long sessionId);
    
    @Query("SELECT SUM(c.testCasesPassed) FROM CodeExecutionResult c WHERE c.answer.id=:aid")
    Integer sumPassedByAnswerId(@Param("aid") Long answerId);
}
