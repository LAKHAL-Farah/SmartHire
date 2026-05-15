package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msroadmap.Entities.RoadmapEdge;

import java.util.List;

public interface RoadmapEdgeRepository extends JpaRepository<RoadmapEdge, Long> {

    List<RoadmapEdge> findByRoadmapId(Long roadmapId);

    List<RoadmapEdge> findByRoadmapIdAndFromNodeId(Long roadmapId, String fromNodeId);

    List<RoadmapEdge> findByRoadmapIdAndToNodeId(Long roadmapId, String toNodeId);

    List<RoadmapEdge> findByRoadmapIdAndFromNode_Id(Long roadmapId, Long fromNodePk);

    List<RoadmapEdge> findByFromNodeId(String fromNodeId);

    List<RoadmapEdge> findByToNodeId(String toNodeId);

    void deleteByRoadmapId(Long roadmapId);
}
