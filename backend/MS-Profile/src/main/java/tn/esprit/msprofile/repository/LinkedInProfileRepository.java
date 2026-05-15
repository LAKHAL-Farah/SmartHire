package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.LinkedInProfile;

import java.util.Optional;
import java.util.UUID;

public interface LinkedInProfileRepository extends JpaRepository<LinkedInProfile, UUID> {
    Optional<LinkedInProfile> findByUserId(UUID userId);
    Optional<LinkedInProfile> findByProfileUrl(String profileUrl);
    boolean existsByUserId(UUID userId);
}

