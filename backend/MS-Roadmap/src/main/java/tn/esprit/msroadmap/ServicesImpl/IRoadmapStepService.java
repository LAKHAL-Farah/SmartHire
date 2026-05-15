
package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.DTO.request.StepRequest; // Added import
import tn.esprit.msroadmap.DTO.response.StepResponse;
import tn.esprit.msroadmap.Enums.StepStatus;
import java.util.List;

public interface IRoadmapStepService {
    StepResponse addStepToRoadmap(Long roadmapId, StepRequest request);

    void deleteStep(Long stepId);

    // Additional methods per spec
    List<StepResponse> getStepsByRoadmapId(Long roadmapId);
    StepResponse getStepById(Long stepId);
    void completeStep(Long stepId, Long userId);
    StepResponse getNextAvailableStep(Long roadmapId);
    List<StepResponse> getStepsByStatus(Long roadmapId, StepStatus status);
}