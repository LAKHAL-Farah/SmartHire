
package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.request.RoadmapRequest;
import tn.esprit.msroadmap.DTO.response.ProgressSummaryDto;
import tn.esprit.msroadmap.DTO.response.RoadmapResponse;
import java.util.List;

public interface IRoadmapService {
    RoadmapResponse createRoadmap(RoadmapRequest request);
    RoadmapResponse getRoadmapById(Long id);
    List<RoadmapResponse> getAllRoadmaps(); // Added this

    RoadmapResponse updateRoadmap(Long id, RoadmapRequest request);

    void deleteRoadmap(Long id);

    // Additional methods per spec
    RoadmapResponse generateRoadmap(RoadmapGenerationRequestDto request);
    RoadmapResponse getRoadmapByUserId(Long userId);
    RoadmapResponse getActiveRoadmapByUserId(Long userId);
    List<RoadmapResponse> getRoadmapsByUserId(Long userId);
    List<RoadmapResponse> getRoadmapsByCareerPath(Long careerPathId);
    void pauseRoadmap(Long id);
    void resumeRoadmap(Long id);
    String shareRoadmap(Long id);
    void unshareRoadmap(Long id);
    RoadmapResponse getRoadmapByShareToken(String token);
    void updateProgress(Long roadmapId);
    ProgressSummaryDto getProgressSummary(Long roadmapId);
}