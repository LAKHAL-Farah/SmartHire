package tn.esprit.msprofile.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.GitHubRepositoryRequest;
import tn.esprit.msprofile.dto.response.GitHubRepositoryResponse;
import tn.esprit.msprofile.entity.GitHubRepository;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.GitHubRepositoryRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GitHubRepositoryService extends AbstractCrudService<GitHubRepository, GitHubRepositoryResponse> {

    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final GitHubProfileRepository gitHubProfileRepository;

    @Override
    protected JpaRepository<GitHubRepository, UUID> repository() {
        return gitHubRepositoryRepository;
    }

    @Override
    protected GitHubRepositoryResponse toResponse(GitHubRepository entity) {
        return new GitHubRepositoryResponse(
                entity.getId(),
                entity.getGithubProfile().getId(),
                entity.getRepoName(),
                entity.getRepoUrl(),
                entity.getLanguage(),
                entity.getStars(),
                entity.getForksCount(),
                entity.getIsForked(),
                entity.getReadmeScore(),
                entity.getHasCiCd(),
                entity.getHasTests(),
                entity.getCodeStructureScore(),
                entity.getDetectedIssues(),
                entity.getUpdatedAt(),
                entity.getOverallScore()
        );
    }

    @Override
    protected String resourceName() {
        return "GitHubRepository";
    }

    public List<GitHubRepositoryResponse> findByGithubProfileId(UUID githubProfileId) {
        return gitHubRepositoryRepository.findByGithubProfileId(githubProfileId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GitHubRepositoryResponse create(GitHubRepositoryRequest request) {
        GitHubRepository entity = new GitHubRepository();
        apply(entity, request);
        return toResponse(gitHubRepositoryRepository.save(entity));
    }

    @Transactional
    public GitHubRepositoryResponse update(UUID id, GitHubRepositoryRequest request) {
        GitHubRepository entity = requireEntity(id);
        apply(entity, request);
        return toResponse(gitHubRepositoryRepository.save(entity));
    }

    private void apply(GitHubRepository entity, GitHubRepositoryRequest request) {
        entity.setGithubProfile(gitHubProfileRepository.findById(request.githubProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found with id=" + request.githubProfileId())));
        entity.setRepoName(request.repoName().trim());
        entity.setRepoUrl(request.repoUrl().trim());
        entity.setLanguage(trimToNull(request.language()));
        entity.setStars(request.stars() != null ? request.stars() : 0);
        entity.setForksCount(request.forksCount() != null ? request.forksCount() : 0);
        entity.setIsForked(request.isForked() != null ? request.isForked() : Boolean.FALSE);
        entity.setReadmeScore(request.readmeScore());
        entity.setHasCiCd(request.hasCiCd());
        entity.setHasTests(request.hasTests());
        entity.setCodeStructureScore(request.codeStructureScore());
        entity.setDetectedIssues(trimToNull(request.detectedIssues()));
        entity.setUpdatedAt(request.updatedAt());
        entity.setOverallScore(request.overallScore());
    }
}

