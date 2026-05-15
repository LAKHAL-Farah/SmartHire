package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.RoadmapMilestone;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Enums.NotificationType;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Repositories.RoadmapMilestoneRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapMilestoneService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapNotificationService;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class RoadmapMilestoneServiceImpl implements IRoadmapMilestoneService {

    private final RoadmapMilestoneRepository repository;
    private final RoadmapRepository roadmapRepository;
    private final IRoadmapNotificationService notificationService;

    @Override
    public List<RoadmapMilestone> getMilestonesByRoadmapId(Long roadmapId) {
        return repository.findByRoadmapId(roadmapId);
    }

    @Override
    public void checkAndUnlockMilestones(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        int completed = roadmap.getCompletedSteps();
        var candidates = repository.findByRoadmapIdAndStepThresholdLessThanEqual(roadmapId, completed);
        for (RoadmapMilestone m : candidates) {
            if (m.getReachedAt() == null) {
                m.setReachedAt(LocalDateTime.now());
                m.setCertificateIssued(false);
                repository.save(m);

                notificationService.createNotification(
                        roadmap.getUserId(),
                        roadmapId,
                        NotificationType.MILESTONE_REACHED,
                        "Milestone reached: " + m.getTitle()
                );
            }
        }
    }

    @Override
    public RoadmapMilestone createMilestone(Long roadmapId, RoadmapMilestone milestone) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        if (milestone.getStepThreshold() <= 0) {
            throw new BusinessException("stepThreshold must be > 0");
        }
        if (roadmap.getTotalSteps() > 0 && milestone.getStepThreshold() > roadmap.getTotalSteps()) {
            throw new BusinessException("stepThreshold cannot exceed roadmap total steps (" + roadmap.getTotalSteps() + ")");
        }
        milestone.setRoadmap(roadmap);
        return repository.save(milestone);
    }
}
