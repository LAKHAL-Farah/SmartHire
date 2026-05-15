package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.msroadmap.Entities.RoadmapNode;
import tn.esprit.msroadmap.Enums.StepStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface RoadmapNodeRepository extends JpaRepository<RoadmapNode, Long> {

    List<RoadmapNode> findByRoadmapId(Long roadmapId);

    List<RoadmapNode> findByRoadmapIdOrderByStepOrderAsc(Long roadmapId);

    Optional<RoadmapNode> findByRoadmapIdAndNodeId(Long roadmapId, String nodeId);

    Optional<RoadmapNode> findByRoadmapIdAndStepOrder(Long roadmapId, int stepOrder);

    List<RoadmapNode> findByRoadmapIdAndStatus(Long roadmapId, StepStatus status);

    Optional<RoadmapNode> findFirstByRoadmapIdAndStatusOrderByStepOrderAsc(Long roadmapId, StepStatus status);

    int countByRoadmapIdAndStatus(Long roadmapId, StepStatus status);

    void deleteByRoadmapId(Long roadmapId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from RoadmapNode n where n.id = :id")
    Optional<RoadmapNode> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from RoadmapNode n where n.roadmap.id = :roadmapId and n.nodeId = :nodeId")
    Optional<RoadmapNode> findByRoadmapIdAndNodeIdForUpdate(@Param("roadmapId") Long roadmapId, @Param("nodeId") String nodeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from RoadmapNode n where n.roadmap.id = :roadmapId and n.stepOrder = :stepOrder")
    Optional<RoadmapNode> findByRoadmapIdAndStepOrderForUpdate(@Param("roadmapId") Long roadmapId, @Param("stepOrder") int stepOrder);
}
