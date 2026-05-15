package tn.esprit.msroadmap.Services;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.DTO.request.RoadmapRequest;
import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.response.ProgressSummaryDto;
import tn.esprit.msroadmap.DTO.response.RoadmapResponse;
import tn.esprit.msroadmap.DTO.response.RoadmapVisualResponse;
import tn.esprit.msroadmap.DTO.response.StepResponse;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import tn.esprit.msroadmap.Enums.StepStatus;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Mapper.RoadmapMapper;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.Repositories.RoadmapPaceSnapshotRepository;
import tn.esprit.msroadmap.Repositories.RoadmapStepRepository;
import tn.esprit.msroadmap.Enums.RoadmapStatus;
import java.util.UUID;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapVisualService;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Transactional
public class RoadmapServiceImpl implements IRoadmapService {

    private final RoadmapRepository roadmapRepository;
    private final RoadmapPaceSnapshotRepository paceSnapshotRepository;
    private final RoadmapStepRepository roadmapStepRepository;
    private final RoadmapMapper roadmapMapper;
    private final IRoadmapVisualService roadmapVisualService;

    @Override
    public RoadmapResponse createRoadmap(RoadmapRequest request) {
        Roadmap roadmap = roadmapMapper.toEntity(request);

        if (request.steps() != null) {
            List<RoadmapStep> steps = request.steps().stream().map(stepReq -> {
                RoadmapStep step = roadmapMapper.toStepEntity(stepReq); // Updated method name
                step.setRoadmap(roadmap);
                return step;
            }).collect(Collectors.toList());
            roadmap.setSteps(steps);
            roadmap.setTotalSteps(steps.size());

            if (!steps.isEmpty()) {
                steps.get(0).setStatus(StepStatus.AVAILABLE);
            }
        }

        roadmap.setCompletedSteps(0);
        roadmap.setStatus(RoadmapStatus.ACTIVE);

        Roadmap savedRoadmap = roadmapRepository.save(roadmap);
        return roadmapMapper.toResponse(savedRoadmap);
    }

    @Override
    public RoadmapResponse getRoadmapById(Long id) {
        Roadmap roadmap = roadmapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found with ID: " + id));
        return roadmapMapper.toResponse(roadmap);
    }

    @Override
    public List<RoadmapResponse> getAllRoadmaps() {
        return roadmapRepository.findAll().stream()
                .map(roadmapMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoadmapResponse updateRoadmap(Long id, RoadmapRequest request) {
        // 1. Find existing roadmap
        Roadmap existingRoadmap = roadmapRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found with ID: " + id));

        // 2. Update all fields from the Request Record
        existingRoadmap.setTitle(request.title());
        existingRoadmap.setCareerPathId(request.careerPathId());
        existingRoadmap.setDifficulty(request.difficulty());
        existingRoadmap.setEstimatedWeeks(request.estimatedWeeks());

        // 3. Sync Steps
        if (request.steps() != null) {
            existingRoadmap.getSteps().clear();

            List<RoadmapStep> newSteps = request.steps().stream().map(stepReq -> {
                RoadmapStep step = roadmapMapper.toStepEntity(stepReq);
                step.setRoadmap(existingRoadmap);
                return step;
            }).collect(Collectors.toList());

            existingRoadmap.getSteps().addAll(newSteps);
        }

        // No need for a manual save if @Transactional is working,
        // but returning the saved object is cleaner.
        return roadmapMapper.toResponse(roadmapRepository.save(existingRoadmap));
    }

    @Override
    public void deleteRoadmap(Long id) {
        if (!roadmapRepository.existsById(id)) {
            throw new ResourceNotFoundException("Roadmap not found with ID: " + id);
        }
        paceSnapshotRepository.deleteByRoadmapId(id);
        // Because of CascadeType.ALL, this will also delete all associated steps
        roadmapRepository.deleteById(id);
    }

    @Override
    public RoadmapResponse generateRoadmap(RoadmapGenerationRequestDto request) {
        RoadmapVisualResponse visual = roadmapVisualService.generateVisualRoadmap(request);
        return getRoadmapById(visual.getRoadmapId());
    }

    @Override
    public RoadmapResponse getRoadmapByUserId(Long userId) {
        List<Roadmap> roadmaps = roadmapRepository.findByUserId(userId);

        if (roadmaps == null || roadmaps.isEmpty()) {
            throw new ResourceNotFoundException("No roadmap found for user: " + userId);
        }

        Roadmap selected = roadmaps.stream()
            .max(Comparator
                .comparingInt((Roadmap r) -> roadmapStatusPriority(r.getStatus()))
                .thenComparing(roadmapRecencyComparator()))
            .orElseThrow(() -> new ResourceNotFoundException("No roadmap found for user: " + userId));

        return roadmapMapper.toResponse(selected);
    }

    @Override
    public RoadmapResponse getActiveRoadmapByUserId(Long userId) {
        List<Roadmap> activeRoadmaps = roadmapRepository.findByUserIdAndStatus(userId, RoadmapStatus.ACTIVE);

        if (activeRoadmaps == null || activeRoadmaps.isEmpty()) {
            throw new ResourceNotFoundException("No active roadmap found for user: " + userId);
        }

        Roadmap selected = activeRoadmaps.stream()
            .max(roadmapRecencyComparator())
            .orElse(activeRoadmaps.get(0));

        return roadmapMapper.toResponse(selected);
    }

    @Override
    public List<RoadmapResponse> getRoadmapsByUserId(Long userId) {
        List<Roadmap> roadmaps = roadmapRepository.findByUserId(userId);
        if (roadmaps == null || roadmaps.isEmpty()) {
            return List.of();
        }

        return roadmaps.stream()
            .sorted(
                Comparator
                    .comparingInt((Roadmap r) -> roadmapStatusPriority(r.getStatus()))
                    .thenComparing(roadmapRecencyComparator())
                    .reversed())
            .map(roadmapMapper::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public List<RoadmapResponse> getRoadmapsByCareerPath(Long careerPathId) {
        return roadmapRepository.findByCareerPathId(careerPathId).stream().map(roadmapMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public void pauseRoadmap(Long id) {
        Roadmap roadmap = roadmapRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        roadmap.setStatus(RoadmapStatus.PAUSED);
        roadmapRepository.save(roadmap);
    }

    @Override
    public void resumeRoadmap(Long id) {
        Roadmap roadmap = roadmapRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        roadmap.setStatus(RoadmapStatus.ACTIVE);
        roadmapRepository.save(roadmap);
    }

    @Override
    public String shareRoadmap(Long id) {
        Roadmap roadmap = roadmapRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        String token = UUID.randomUUID().toString();
        roadmap.setShareToken(token);
        roadmap.setPublic(true);
        roadmapRepository.save(roadmap);
        return token;
    }

    @Override
    public void unshareRoadmap(Long id) {
        Roadmap roadmap = roadmapRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        roadmap.setShareToken(null);
        roadmap.setPublic(false);
        roadmapRepository.save(roadmap);
    }

    @Override
    public RoadmapResponse getRoadmapByShareToken(String token) {
        Roadmap roadmap = roadmapRepository.findByShareToken(token);
        if (roadmap == null) throw new ResourceNotFoundException("Public roadmap not found");
        return roadmapMapper.toResponse(roadmap);
    }

    @Override
    public void updateProgress(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findByIdForUpdate(roadmapId)
            .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        long completed = roadmapStepRepository.countByRoadmapIdAndStatus(roadmapId, tn.esprit.msroadmap.Enums.StepStatus.COMPLETED);
        roadmap.setCompletedSteps((int) completed);
        if (roadmap.getTotalSteps() > 0 && roadmap.getCompletedSteps() >= roadmap.getTotalSteps()) {
            roadmap.setStatus(RoadmapStatus.COMPLETED);
        }
        roadmapRepository.save(roadmap);
    }

        @Override
        public ProgressSummaryDto getProgressSummary(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
            .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        List<RoadmapStep> steps = roadmapStepRepository.findByRoadmapIdOrderByStepOrderAsc(roadmapId);
        RoadmapStep currentStep = steps.stream()
            .filter(s -> s.getStatus() == StepStatus.AVAILABLE || s.getStatus() == StepStatus.IN_PROGRESS)
            .findFirst()
            .orElse(null);

        return ProgressSummaryDto.builder()
            .roadmapId(roadmapId)
            .totalSteps(roadmap.getTotalSteps())
            .completedSteps(roadmap.getCompletedSteps())
            .progressPercent(roadmap.getTotalSteps() == 0 ? 0 :
                (roadmap.getCompletedSteps() * 100.0) / roadmap.getTotalSteps())
            .streakDays(roadmap.getStreakDays())
            .currentStep(currentStep != null ?
                new StepResponse(
                    currentStep.getId(),
                    currentStep.getStepOrder(),
                    currentStep.getTitle(),
                    currentStep.getObjective(),
                    currentStep.getEstimatedDays(),
                    currentStep.getStatus() != null ? currentStep.getStatus().name() : null
                ) : null)
            .build();
        }

    private Comparator<Roadmap> roadmapRecencyComparator() {
        return Comparator
            .comparing(Roadmap::getGeneratedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                (Roadmap r) -> r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Roadmap::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private int roadmapStatusPriority(RoadmapStatus status) {
        if (status == null) {
            return 0;
        }

        return switch (status) {
            case ACTIVE -> 5;
            case COMPLETED -> 4;
            case PAUSED -> 3;
            case GENERATING -> 2;
            case ARCHIVED -> 1;
        };
    }
}