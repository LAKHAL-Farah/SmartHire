package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.UserStepBenchmark;

import java.util.List;

public interface IUserStepBenchmarkService {
    List<UserStepBenchmark> getByUserId(Long userId);
    UserStepBenchmark getByUserIdAndStepId(Long userId, Long stepId);
}
