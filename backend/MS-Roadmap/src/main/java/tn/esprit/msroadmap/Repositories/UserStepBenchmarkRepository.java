package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.UserStepBenchmark;

import java.util.List;

@Repository
public interface UserStepBenchmarkRepository extends JpaRepository<UserStepBenchmark, Long> {
    List<UserStepBenchmark> findByUserId(Long userId);
    UserStepBenchmark findByUserIdAndRoadmapStepId(Long userId, Long stepId);
}
