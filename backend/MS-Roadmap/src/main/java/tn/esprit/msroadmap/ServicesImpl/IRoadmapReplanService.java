package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.RoadmapReplanEvent;

import java.util.List;

public interface IRoadmapReplanService {
    RoadmapReplanEvent replanRoadmap(Long roadmapId, String reason, String newPlan);
    List<RoadmapReplanEvent> getReplanHistory(Long roadmapId);
}
