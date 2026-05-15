package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.RoadmapMilestone;

import java.util.List;

@Repository
public interface RoadmapMilestoneRepository extends JpaRepository<RoadmapMilestone, Long> {
    List<RoadmapMilestone> findByRoadmapId(Long roadmapId);
    List<RoadmapMilestone> findByRoadmapIdAndReachedAtIsNull(Long roadmapId);
    List<RoadmapMilestone> findByRoadmapIdAndStepThresholdLessThanEqual(Long roadmapId, int completedSteps);
}
