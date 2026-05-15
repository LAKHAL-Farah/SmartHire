package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.UserStepBenchmark;
import tn.esprit.msroadmap.Repositories.UserStepBenchmarkRepository;
import tn.esprit.msroadmap.ServicesImpl.IUserStepBenchmarkService;

import java.util.List;

@Service
@AllArgsConstructor
public class UserStepBenchmarkServiceImpl implements IUserStepBenchmarkService {

    private final UserStepBenchmarkRepository repository;

    @Override
    public List<UserStepBenchmark> getByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public UserStepBenchmark getByUserIdAndStepId(Long userId, Long stepId) {
        return repository.findByUserIdAndRoadmapStepId(userId, stepId);
    }
}
