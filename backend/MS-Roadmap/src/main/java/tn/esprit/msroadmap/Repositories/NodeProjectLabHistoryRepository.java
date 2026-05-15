package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.NodeProjectLabHistory;

import java.util.List;
import java.util.Optional;

@Repository
public interface NodeProjectLabHistoryRepository extends JpaRepository<NodeProjectLabHistory, Long> {
    Optional<NodeProjectLabHistory> findFirstByNodeIdAndUserIdOrderByGeneratedAtDesc(Long nodeId, Long userId);
    List<NodeProjectLabHistory> findTop12ByNodeIdAndUserIdOrderByGeneratedAtDesc(Long nodeId, Long userId);
}
