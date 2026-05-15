package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.GitHubProfile;

import java.util.Optional;
import java.util.UUID;

public interface GitHubProfileRepository extends JpaRepository<GitHubProfile, UUID> {
    Optional<GitHubProfile> findByUserId(UUID userId);
    Optional<GitHubProfile> findByGithubUsername(String username);
    boolean existsByUserId(UUID userId);
    boolean existsByGithubUsername(String username);
}

