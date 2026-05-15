package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.HireReadinessScore;

import java.util.Optional;
import java.util.UUID;

public interface HireReadinessScoreRepository extends JpaRepository<HireReadinessScore, UUID> {
    Optional<HireReadinessScore> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}

