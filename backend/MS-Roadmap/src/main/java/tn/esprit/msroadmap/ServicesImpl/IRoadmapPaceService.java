package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.RoadmapPaceSnapshot;

import java.util.List;

public interface IRoadmapPaceService {
    RoadmapPaceSnapshot takeSnapshot(Long roadmapId);
    List<RoadmapPaceSnapshot> getSnapshotHistory(Long roadmapId);
    RoadmapPaceSnapshot getLatestSnapshot(Long roadmapId);
    String computeCatchUpPlan(Long roadmapId);
}
