package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.UserStepProgress;
import java.util.Optional;

@Repository
public interface UserStepProgressRepository extends JpaRepository<UserStepProgress, Long> {
    // Custom finder to get progress of a specific step within a user's roadmap
    Optional<UserStepProgress> findByUserRoadmapIdAndStepId(Long userRoadmapId, Long stepId);
}