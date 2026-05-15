package tn.esprit.msroadmap.Services;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.UserRoadmap;
import tn.esprit.msroadmap.Entities.UserStepProgress;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.UserRoadmapRepository;
import tn.esprit.msroadmap.Repositories.UserStepProgressRepository;
import tn.esprit.msroadmap.ServicesImpl.IUserStepProgressService;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class UserStepProgressServiceImpl implements IUserStepProgressService {

    private final UserStepProgressRepository progressRepository;
    private final UserRoadmapRepository userRoadmapRepository;

    @Override
    @Transactional
    public void updateStepStatus(Long userRoadmapId, Long stepId, String status) {
        UserRoadmap userRoadmap = userRoadmapRepository.findById(userRoadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("User Roadmap not found"));

        // Note: Ensure this method exists in your UserStepProgressRepository interface
        UserStepProgress progress = progressRepository.findByUserRoadmapIdAndStepId(userRoadmapId, stepId)
                .orElse(new UserStepProgress());

        progress.setUserRoadmap(userRoadmap);
        progress.setStepId(stepId);
        progress.setStatus(status);

        if ("COMPLETED".equalsIgnoreCase(status)) {
            progress.setCompleteAt(LocalDateTime.now());
        }

        progressRepository.save(progress);
    }
}