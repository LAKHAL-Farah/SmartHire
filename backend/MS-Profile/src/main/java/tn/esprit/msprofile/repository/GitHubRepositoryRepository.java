package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.GitHubRepository;

import java.util.List;
import java.util.UUID;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, UUID> {
    List<GitHubRepository> findByGithubProfileId(UUID githubProfileId);
    List<GitHubRepository> findByGithubProfileIdOrderByOverallScoreDesc(UUID githubProfileId);
    List<GitHubRepository> findByGithubProfileIdAndLanguage(UUID githubProfileId, String language);
    void deleteByGithubProfileId(UUID githubProfileId);
    int countByGithubProfileId(UUID githubProfileId);
    int countByGithubProfileIdAndHasCiCdTrue(UUID githubProfileId);
    int countByGithubProfileIdAndHasTestsTrue(UUID githubProfileId);
}

