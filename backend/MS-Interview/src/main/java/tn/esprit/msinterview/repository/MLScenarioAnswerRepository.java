package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msinterview.entity.MLScenarioAnswer;

import java.util.List;
import java.util.Optional;

public interface MLScenarioAnswerRepository extends JpaRepository<MLScenarioAnswer, Long> {
    Optional<MLScenarioAnswer> findByAnswerId(Long answerId);
    List<MLScenarioAnswer> findByAnswerSessionId(Long sessionId);
    boolean existsByAnswerId(Long answerId);
}
