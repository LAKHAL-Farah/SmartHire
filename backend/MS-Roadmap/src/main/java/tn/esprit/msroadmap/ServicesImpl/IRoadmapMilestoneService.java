package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.RoadmapMilestone;

import java.util.List;

public interface IRoadmapMilestoneService {
    List<RoadmapMilestone> getMilestonesByRoadmapId(Long roadmapId);
    void checkAndUnlockMilestones(Long roadmapId);
    RoadmapMilestone createMilestone(Long roadmapId, RoadmapMilestone milestone);
}
