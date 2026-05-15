package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateCVRepository extends JpaRepository<CandidateCV, UUID> {
    List<CandidateCV> findByUserId(UUID userId);
    List<CandidateCV> findByUserIdAndIsActiveTrue(UUID userId);
    Optional<CandidateCV> findByIdAndUserId(UUID id, UUID userId);
    List<CandidateCV> findByUserIdAndParseStatus(UUID userId, ProcessingStatus status);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}

