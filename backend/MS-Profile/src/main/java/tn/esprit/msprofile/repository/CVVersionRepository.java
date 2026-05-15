package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.CVVersion;
import tn.esprit.msprofile.entity.enums.CVVersionType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CVVersionRepository extends JpaRepository<CVVersion, UUID> {
    List<CVVersion> findByCvId(UUID cvId);
    List<CVVersion> findByCvIdAndVersionType(UUID cvId, CVVersionType type);
    Optional<CVVersion> findByCvIdAndJobOfferId(UUID cvId, UUID jobOfferId);
    Optional<CVVersion> findTopByCvIdOrderByGeneratedAtDesc(UUID cvId);
    List<CVVersion> findByCv_UserId(UUID userId);
    boolean existsByCvIdAndJobOfferId(UUID cvId, UUID jobOfferId);
    List<CVVersion> findByJobOfferId(UUID jobOfferId);
}

