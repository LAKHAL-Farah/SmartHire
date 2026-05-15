package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.UserRoadmap;
import java.util.Optional;

@Repository
public interface UserRoadmapRepository extends JpaRepository<UserRoadmap, Long> {
    // Custom finder to check if a user is already enrolled in a roadmap
    Optional<UserRoadmap> findByUserIdAndRoadmapId(Long userId, Long roadmapId);
}