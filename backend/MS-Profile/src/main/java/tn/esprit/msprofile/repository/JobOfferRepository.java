package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.JobOffer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {
    List<JobOffer> findByUserId(UUID userId);
    Optional<JobOffer> findByIdAndUserId(UUID id, UUID userId);
    List<JobOffer> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}

