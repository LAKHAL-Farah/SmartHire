package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.PeerBenchmark;
import tn.esprit.msroadmap.Entities.UserStepBenchmark;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.PeerBenchmarkRepository;
import tn.esprit.msroadmap.Repositories.UserStepBenchmarkRepository;
import tn.esprit.msroadmap.ServicesImpl.IPeerBenchmarkService;

import java.util.List;

@Service
@AllArgsConstructor
public class PeerBenchmarkServiceImpl implements IPeerBenchmarkService {

    private final PeerBenchmarkRepository repository;
    private final UserStepBenchmarkRepository userRepo;

    @Override
    public PeerBenchmark getBenchmarkForStep(Long careerPathId, int stepOrder) {
        PeerBenchmark p = repository.findByCareerPathIdAndStepOrder(careerPathId, stepOrder);
        if (p == null) throw new ResourceNotFoundException("Benchmark not found");
        return p;
    }

    @Override
    public List<PeerBenchmark> getBenchmarksForCareerPath(Long careerPathId) {
        return repository.findByCareerPathId(careerPathId);
    }

    @Override
    public String getBenchmarkForUser(Long userId, Long stepId) {
        UserStepBenchmark u = userRepo.findByUserIdAndRoadmapStepId(userId, stepId);
        if (u == null) return "No record";
        return "User days: " + u.getUserDays();
    }

    @Override
    public void recordUserStepCompletion(Long userId, Long stepId, int actualDays) {
        UserStepBenchmark existing = userRepo.findByUserIdAndRoadmapStepId(userId, stepId);
        if (existing == null) {
            UserStepBenchmark nb = new UserStepBenchmark();
            nb.setUserId(userId);
            // link to step via id only to keep compile-time simple
            userRepo.save(nb);
        } else {
            existing.setUserDays(actualDays);
            userRepo.save(existing);
        }
    }

    @Override
    public void recomputeBenchmarks(Long careerPathId) {
        // stub: would run async batch to recompute p10/p90
    }
}
