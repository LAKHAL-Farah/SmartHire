package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Entities.RoadmapPaceSnapshot;
import tn.esprit.msroadmap.Enums.PaceStatus;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.RoadmapPaceSnapshotRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapPaceService;

import java.time.LocalDate;
import java.util.List;

@Service
@AllArgsConstructor
public class RoadmapPaceServiceImpl implements IRoadmapPaceService {

    private final RoadmapPaceSnapshotRepository repository;
    private final RoadmapRepository roadmapRepository;

    @Override
    public RoadmapPaceSnapshot takeSnapshot(Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));
        LocalDate today = LocalDate.now();
        RoadmapPaceSnapshot existing = repository.findByRoadmapIdAndSnapshotDate(roadmapId, today);
        if (existing != null) {
            return existing;
        }
        RoadmapPaceSnapshot s = new RoadmapPaceSnapshot();
        s.setRoadmap(roadmap);
        s.setSnapshotDate(today);
        s.setPlannedSteps(roadmap.getTotalSteps());
        s.setCompletedSteps(roadmap.getCompletedSteps());
        s.setTimedeltaDays(0);
        s.setPaceStatus(PaceStatus.ON_TRACK);
        return repository.save(s);
    }

    @Override
    public List<RoadmapPaceSnapshot> getSnapshotHistory(Long roadmapId) {
        return repository.findByRoadmapIdOrderBySnapshotDateDesc(roadmapId);
    }

    @Override
    public RoadmapPaceSnapshot getLatestSnapshot(Long roadmapId) {
        return repository.findTopByRoadmapIdOrderBySnapshotDateDesc(roadmapId);
    }

    @Override
    public String computeCatchUpPlan(Long roadmapId) {
        RoadmapPaceSnapshot latest = getLatestSnapshot(roadmapId);
        if (latest == null) {
            return "No pace snapshot available yet. Capture a snapshot after some progress to get a catch-up plan.";
        }

        int delta = latest.getCompletedSteps() - latest.getPlannedSteps();
        if (latest.getPaceStatus() == PaceStatus.BEHIND || delta < 0) {
            return "You are behind schedule by " + Math.abs(delta) + " step(s). Focus on finishing the next available roadmap items before taking on new work.";
        }

        if (latest.getPaceStatus() == PaceStatus.AHEAD || delta > 0) {
            return "You are ahead of schedule by " + delta + " step(s). Keep the momentum and consider taking on the next step early.";
        }

        return "You are on track. Keep your current pace and continue with the next available step.";
    }
}
