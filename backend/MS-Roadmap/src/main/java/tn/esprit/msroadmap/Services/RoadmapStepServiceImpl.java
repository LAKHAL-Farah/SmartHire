package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msroadmap.DTO.request.StepRequest;
import tn.esprit.msroadmap.DTO.response.StepResponse;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Mapper.RoadmapMapper;
import tn.esprit.msroadmap.Repositories.RoadmapNodeRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.Repositories.RoadmapStepRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapStepService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapMilestoneService;
import tn.esprit.msroadmap.Enums.StepStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RoadmapStepServiceImpl implements IRoadmapStepService {

    private final RoadmapStepRepository stepRepository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapNodeRepository nodeRepository;
    private final RoadmapMapper roadmapMapper;
    private final IRoadmapService roadmapService;
    private final IRoadmapMilestoneService milestoneService;

    @Override
    public StepResponse addStepToRoadmap(Long roadmapId, StepRequest request) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        RoadmapStep step = roadmapMapper.toStepEntity(request); // Updated method name
        step.setRoadmap(roadmap);

        return roadmapMapper.toStepResponse(stepRepository.save(step));
    }

    @Override
    public void deleteStep(Long stepId) {
        if (!stepRepository.existsById(stepId)) {
            throw new ResourceNotFoundException("Step not found with ID: " + stepId);
        }
        stepRepository.deleteById(stepId);
    }

    @Override
    public List<StepResponse> getStepsByRoadmapId(Long roadmapId) {
        return stepRepository.findByRoadmapIdOrderByStepOrderAsc(roadmapId).stream().map(roadmapMapper::toStepResponse).collect(Collectors.toList());
    }

    @Override
    public StepResponse getStepById(Long stepId) {
        RoadmapStep step = stepRepository.findById(stepId).orElseThrow(() -> new ResourceNotFoundException("Step not found"));
        return roadmapMapper.toStepResponse(step);
    }

    @Override
    @Transactional
    public void completeStep(Long stepId, Long userId) {
        RoadmapStep step = stepRepository.findByIdForUpdate(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Step not found"));
        validateOwnership(step.getRoadmap(), userId);

        if (step.getStatus() != StepStatus.AVAILABLE && step.getStatus() != StepStatus.IN_PROGRESS) {
            throw new BusinessException("Step is not available for completion");
        }

        LocalDateTime now = LocalDateTime.now();
        step.setStatus(StepStatus.COMPLETED);
        step.setCompletedAt(now);

        if (step.getUnlockedAt() != null) {
            long days = ChronoUnit.DAYS.between(step.getUnlockedAt(), now);
            step.setActualDays((int) Math.max(1, days));
        }
        stepRepository.save(step);
        syncNodeFromStep(step);

        Long roadmapId = step.getRoadmap().getId();
        if (step.getStepOrder() != null) {
            stepRepository.findByRoadmapIdAndStepOrderForUpdate(roadmapId, step.getStepOrder() + 1)
                    .ifPresent(nextLocked -> {
                        if (nextLocked.getStatus() == StepStatus.LOCKED) {
                            nextLocked.setStatus(StepStatus.AVAILABLE);
                            nextLocked.setUnlockedAt(now);
                            stepRepository.save(nextLocked);
                            syncNodeFromStep(nextLocked);
                        }
                    });
        }

        roadmapService.updateProgress(roadmapId);

        Roadmap roadmap = roadmapRepository.findByIdForUpdate(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        RoadmapProgressCalculator.updateStreakDays(roadmap, LocalDate.now());
        roadmapRepository.save(roadmap);

        milestoneService.checkAndUnlockMilestones(roadmapId);
    }

    @Override
    public StepResponse getNextAvailableStep(Long roadmapId) {
        RoadmapStep next = stepRepository.findFirstByRoadmapIdAndStatusOrderByStepOrderAsc(roadmapId, StepStatus.AVAILABLE);
        if (next == null) throw new ResourceNotFoundException("No available step");
        return roadmapMapper.toStepResponse(next);
    }

    @Override
    public List<StepResponse> getStepsByStatus(Long roadmapId, StepStatus status) {
        return stepRepository.findByRoadmapIdAndStatus(roadmapId, status).stream().map(roadmapMapper::toStepResponse).collect(Collectors.toList());
    }

    private void validateOwnership(Roadmap roadmap, Long userId) {
        if (userId == null) {
            throw new BusinessException("userId is required");
        }

        if (roadmap.getUserId() != null && !roadmap.getUserId().equals(userId)) {
            throw new BusinessException("User does not own this roadmap");
        }
    }

    private void syncNodeFromStep(RoadmapStep step) {
        if (step.getStepOrder() == null) {
            return;
        }

        nodeRepository.findByRoadmapIdAndStepOrderForUpdate(step.getRoadmap().getId(), step.getStepOrder())
                .ifPresent(node -> {
                    node.setStatus(step.getStatus());
                    node.setUnlockedAt(step.getUnlockedAt());
                    node.setCompletedAt(step.getCompletedAt());
                    node.setActualDays(step.getActualDays());
                    nodeRepository.save(node);
                });
    }
}