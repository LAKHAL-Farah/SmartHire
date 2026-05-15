package tn.esprit.msroadmap.ServicesImpl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

public interface IUserStepProgressService {
    void updateStepStatus(Long userRoadmapId, Long stepId, String status);
}
