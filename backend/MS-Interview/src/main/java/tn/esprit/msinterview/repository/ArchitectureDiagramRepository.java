package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msinterview.entity.ArchitectureDiagram;

import java.util.List;
import java.util.Optional;

public interface ArchitectureDiagramRepository extends JpaRepository<ArchitectureDiagram, Long> {
    Optional<ArchitectureDiagram> findByAnswerId(Long answerId);
    List<ArchitectureDiagram> findByAnswerSessionId(Long sessionId);
    boolean existsByAnswerId(Long answerId);
}
