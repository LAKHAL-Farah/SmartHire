package tn.esprit.msprofile.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.HireReadinessScoreRequest;
import tn.esprit.msprofile.dto.response.HireReadinessScoreResponse;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.HireReadinessScore;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.DuplicateResourceException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.HireReadinessScoreRepository;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HireReadinessScoreService extends AbstractCrudService<HireReadinessScore, HireReadinessScoreResponse> {

    private final HireReadinessScoreRepository hireReadinessScoreRepository;
    private final CandidateCVRepository candidateCVRepository;
    private final LinkedInProfileRepository linkedInProfileRepository;
    private final GitHubProfileRepository gitHubProfileRepository;
    private final AuditLogService auditLogService;

    @Override
    protected JpaRepository<HireReadinessScore, UUID> repository() {
        return hireReadinessScoreRepository;
    }

    @Override
    protected HireReadinessScoreResponse toResponse(HireReadinessScore entity) {
        return new HireReadinessScoreResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getCvScore(),
                entity.getLinkedinScore(),
                entity.getGithubScore(),
                entity.getGlobalScore(),
                entity.getComputedAt()
        );
    }


    @Override
    protected String resourceName() {
        return "HireReadinessScore";
    }

    @Transactional
    public HireReadinessScoreResponse computeAndSaveScore(UUID userId) {
        long startedAt = System.currentTimeMillis();
        int cvScore = extractLatestCvScore(userId);
        int linkedInScore = linkedInProfileRepository.findByUserId(userId)
                .map(LinkedInProfile::getGlobalScore)
                .filter(score -> score != null)
                .orElse(0);
        int gitHubScore = gitHubProfileRepository.findByUserId(userId)
                .map(GitHubProfile::getOverallScore)
                .filter(score -> score != null)
                .orElse(0);

        int globalScore = (int) Math.round((cvScore * 0.40) + (linkedInScore * 0.30) + (gitHubScore * 0.30));

        HireReadinessScore score = hireReadinessScoreRepository.findByUserId(userId).orElseGet(HireReadinessScore::new);
        score.setUserId(userId);
        score.setCvScore(cvScore);
        score.setLinkedinScore(linkedInScore);
        score.setGithubScore(gitHubScore);
        score.setGlobalScore(globalScore);
        score.setComputedAt(Instant.now());
        HireReadinessScore saved = hireReadinessScoreRepository.save(score);
        auditLogService.logOperation(
            userId,
            OperationType.SCORE_COMPUTE,
            "HireReadinessScore",
            saved.getId(),
            ProcessingStatus.COMPLETED,
            500,
            (int) (System.currentTimeMillis() - startedAt),
            null
        );

        return toResponse(saved);
    }

    public HireReadinessScoreResponse getScoreForUser(UUID userId) {
        return toResponse(hireReadinessScoreRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("HireReadinessScore not found for userId=" + userId)));
    }

    @Transactional
    public HireReadinessScoreResponse refreshScore(UUID userId) {
        return computeAndSaveScore(userId);
    }

    public HireReadinessScoreResponse findByUserId(UUID userId) {
        return toResponse(hireReadinessScoreRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("HireReadinessScore not found for userId=" + userId)));
    }

    @Transactional
    public HireReadinessScoreResponse create(HireReadinessScoreRequest request) {
        validateUniqueUserId(request.userId(), null);
        HireReadinessScore entity = new HireReadinessScore();
        apply(entity, request);
        return toResponse(hireReadinessScoreRepository.save(entity));
    }

    @Transactional
    public HireReadinessScoreResponse update(UUID id, HireReadinessScoreRequest request) {
        HireReadinessScore entity = requireEntity(id);
        validateUniqueUserId(request.userId(), id);
        apply(entity, request);
        return toResponse(hireReadinessScoreRepository.save(entity));
    }

    private void validateUniqueUserId(UUID userId, UUID currentId) {
        hireReadinessScoreRepository.findByUserId(userId).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new DuplicateResourceException("A HireReadinessScore already exists for userId=" + userId);
            }
        });
    }

    private void apply(HireReadinessScore entity, HireReadinessScoreRequest request) {
        entity.setUserId(request.userId());
        entity.setCvScore(request.cvScore());
        entity.setLinkedinScore(request.linkedinScore());
        entity.setGithubScore(request.githubScore());
        entity.setGlobalScore(request.globalScore());
        entity.setComputedAt(request.computedAt());
    }

    private int extractLatestCvScore(UUID userId) {
        Optional<CandidateCV> activeCv = candidateCVRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .max(Comparator.comparing(CandidateCV::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        if (activeCv.isPresent()) {
            Integer score = activeCv.get().getAtsScore();
            return score == null ? 0 : score;
        }

        return candidateCVRepository.findByUserId(userId).stream()
                .max(Comparator.comparing(CandidateCV::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CandidateCV::getAtsScore)
                .filter(score -> score != null)
                .orElse(0);
    }
}

