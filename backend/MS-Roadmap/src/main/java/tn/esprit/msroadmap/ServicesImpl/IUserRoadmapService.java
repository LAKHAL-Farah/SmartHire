package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.DTO.response.UserRoadmapResponse;

public interface IUserRoadmapService {
    UserRoadmapResponse startRoadmap(Long userId, Long roadmapId);
    UserRoadmapResponse getUserProgress(Long userId, Long roadmapId);

    void deleteUserRoadmap(Long id);
}