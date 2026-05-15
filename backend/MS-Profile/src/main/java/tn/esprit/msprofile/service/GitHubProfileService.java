package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tn.esprit.msprofile.dto.request.GitHubProfileRequest;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.dto.response.GitHubRepoResponse;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.GitHubRepository;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.DuplicateResourceException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.GitHubRepositoryRepository;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubProfileService extends AbstractCrudService<GitHubProfile, GitHubProfileResponse> {

    private final GitHubProfileRepository gitHubProfileRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Value("${app.github-api-base-url:https://api.github.com}")
    private String githubApiBaseUrl;

    @Override
    protected JpaRepository<GitHubProfile, UUID> repository() {
        return gitHubProfileRepository;
    }

    @Override
    protected GitHubProfileResponse toResponse(GitHubProfile entity) {
        List<GitHubRepoResponse> repositories = gitHubRepositoryRepository
            .findByGithubProfileIdOrderByOverallScoreDesc(entity.getId())
            .stream()
            .map(repo -> new GitHubRepoResponse(
                repo.getId(),
                repo.getRepoName(),
                repo.getRepoUrl(),
                repo.getDescription(),
                repo.getLanguage(),
                repo.getStars(),
                repo.getForksCount(),
                repo.getIsForked(),
                repo.getIsArchived(),
                repo.getPushedAt() == null ? null : repo.getPushedAt().toString(),
                repo.getReadmeScore(),
                repo.getHasCiCd(),
                repo.getHasTests(),
                repo.getCodeStructureScore(),
                repo.getAuditFeedback(),
                repo.getFixSuggestions(),
                repo.getOverallScore()
            ))
            .toList();

        return new GitHubProfileResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getGithubUsername(),
            entity.getProfileUrl(),
                entity.getOverallScore(),
                entity.getRepoCount(),
                entity.getTopLanguages(),
                entity.getProfileReadmeScore(),
                entity.getFeedback(),
                entity.getAuditStatus(),
                entity.getAuditErrorMessage(),
                entity.getCreatedAt(),
            entity.getAnalyzedAt(),
            repositories
        );
    }

    @Override
    protected String resourceName() {
        return "GitHubProfile";
    }

    @Transactional
    public GitHubProfileResponse auditGitHubProfile(UUID userId, String githubUsername) {
        long startedAt = System.currentTimeMillis();
        String username = githubUsername == null ? "" : githubUsername.trim();
        if (username.isBlank()) {
            throw new IllegalArgumentException("GitHub username must not be blank");
        }
        // UPSERT behavior: prefer existing profile by githubUsername to avoid duplicate inserts
        GitHubProfile profile;
        boolean isNewProfile = false;

        var byUsername = gitHubProfileRepository.findByGithubUsername(username);
        if (byUsername.isPresent()) {
            profile = byUsername.get();
            // preserve existing user association; only set if it's currently null
            if (profile.getUserId() == null) {
                profile.setUserId(userId);
            }
        } else {
            // fallback: reuse profile by userId if present, else create new
            profile = gitHubProfileRepository.findByUserId(userId).orElseGet(GitHubProfile::new);
            isNewProfile = profile.getId() == null;
            if (profile.getUserId() == null) {
                profile.setUserId(userId);
            }
            profile.setGithubUsername(username);
        }

        profile.setGithubUsername(username);
        profile.setAuditStatus(ProcessingStatus.IN_PROGRESS);
        profile.setAuditErrorMessage(null);
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(Instant.now());
        }
        profile.setAnalyzedAt(Instant.now());
        profile = gitHubProfileRepository.save(profile);

        try {
            List<GitHubRepoRaw> rawRepos = fetchRepositories(username);
            gitHubRepositoryRepository.deleteByGithubProfileId(profile.getId());

            List<GitHubRepository> auditedRepos = new ArrayList<>();
            for (GitHubRepoRaw rawRepo : rawRepos) {
                auditedRepos.add(auditRepository(rawRepo, profile.getId()));
            }

            profile.setRepoCount(auditedRepos.size());
            profile.setTopLanguages(buildTopLanguagesJson(auditedRepos));
            profile.setProfileReadmeScore(calculateProfileReadmeScore(auditedRepos));
            profile.setOverallScore(computeProfileScore(profile.getId()));
            profile.setFeedback(buildAuditFeedback(auditedRepos, profile.getOverallScore()));
            profile.setAuditStatus(ProcessingStatus.COMPLETED);
            profile.setAuditErrorMessage(null);
            profile.setAnalyzedAt(Instant.now());
            GitHubProfile saved = gitHubProfileRepository.save(profile);
            auditLogService.logOperation(
                    userId,
                    OperationType.GITHUB_AUDIT,
                    "GitHubProfile",
                    saved.getId(),
                    ProcessingStatus.COMPLETED,
                    1600,
                    (int) (System.currentTimeMillis() - startedAt),
                    null
            );
            return toResponse(saved);
        } catch (Exception e) {
            log.warn("GitHub audit failed for user {} / {}: {}", userId, username, e.getMessage());
            profile.setAuditStatus(ProcessingStatus.FAILED);
            profile.setAuditErrorMessage(truncate(e.getMessage(), 500));
            gitHubProfileRepository.save(profile);
            auditLogService.logOperation(
                    userId,
                    OperationType.GITHUB_AUDIT,
                    "GitHubProfile",
                    profile.getId(),
                    ProcessingStatus.FAILED,
                    200,
                    (int) (System.currentTimeMillis() - startedAt),
                    e.getMessage()
            );
            return toResponse(profile);
        }
    }

    public List<GitHubRepoRaw> fetchRepositories(String githubUsername) {
        String baseUrl = githubApiBaseUrl == null || githubApiBaseUrl.isBlank()
                ? "https://api.github.com"
                : githubApiBaseUrl.replaceAll("/+$", "");
        String url = baseUrl + "/users/" + githubUsername + "/repos?per_page=100&sort=updated";
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("User-Agent", "SmartHire-MS-Profile");

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to fetch GitHub repositories for username=" + githubUsername);
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.isArray()) {
                return List.of();
            }

            List<GitHubRepoRaw> repositories = new ArrayList<>();
            for (JsonNode node : root) {
                repositories.add(new GitHubRepoRaw(
                        node.path("name").asText(""),
                        node.path("html_url").asText(""),
                        node.path("language").isNull() ? null : node.path("language").asText(null),
                        node.path("stargazers_count").asInt(0),
                        node.path("forks_count").asInt(0),
                        node.path("fork").asBoolean(false),
                        parseInstant(node.path("pushed_at").asText(null)),
                        node.path("description").isNull() ? null : node.path("description").asText(null),
                        node.path("archived").asBoolean(false)
                ));
            }
            return repositories;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse GitHub repositories payload", e);
        }
    }

    @Transactional
    public GitHubRepository auditRepository(GitHubRepoRaw repo, UUID profileId) {
        GitHubProfile profile = gitHubProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found with id=" + profileId));

        int readmeScore = scoreReadmePresence(repo);
        boolean hasCiCd = detectCiCdSignals(repo);
        boolean hasTests = detectTestSignals(repo);
        int codeStructureScore = scoreCodeStructure(repo, hasCiCd, hasTests);

        List<String> detectedIssues = new ArrayList<>();
        if (repo.archived()) {
            detectedIssues.add("Repository is archived");
        }
        if (!hasCiCd) {
            detectedIssues.add("No CI/CD signal detected");
        }
        if (!hasTests) {
            detectedIssues.add("No test signal detected");
        }
        if (readmeScore < 50) {
            detectedIssues.add("README quality appears weak");
        }

        int overallScore = computeRepositoryOverallScore(readmeScore, hasCiCd, hasTests, codeStructureScore, repo.stargazersCount());

        GitHubRepository audited = new GitHubRepository();
        audited.setGithubProfile(profile);
        audited.setRepoName(repo.name());
        audited.setRepoUrl(repo.htmlUrl());
        audited.setLanguage(repo.language());
        audited.setStars(repo.stargazersCount());
        audited.setForksCount(repo.forksCount());
        audited.setIsForked(repo.fork());
        audited.setReadmeScore(readmeScore);
        audited.setHasCiCd(hasCiCd);
        audited.setHasTests(hasTests);
        audited.setCodeStructureScore(codeStructureScore);
        audited.setDetectedIssues(detectedIssues.isEmpty() ? null : String.join("; ", detectedIssues));
        audited.setUpdatedAt(repo.pushedAt());
        audited.setOverallScore(overallScore);

        return gitHubRepositoryRepository.save(audited);
    }

    @Transactional
    public int computeProfileScore(UUID profileId) {
        GitHubProfile profile = gitHubProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found with id=" + profileId));

        List<GitHubRepository> repositories = gitHubRepositoryRepository.findByGithubProfileId(profileId);
        if (repositories.isEmpty()) {
            int fallback = profile.getProfileReadmeScore() == null ? 0 : profile.getProfileReadmeScore();
            profile.setOverallScore(fallback);
            gitHubProfileRepository.save(profile);
            return fallback;
        }

        double repoAverage = repositories.stream()
                .map(GitHubRepository::getOverallScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        int profileReadmeScore = profile.getProfileReadmeScore() == null ? 0 : profile.getProfileReadmeScore();
        int global = Math.min(100, Math.max(0, (int) Math.round((repoAverage * 0.85) + (profileReadmeScore * 0.15))));

        profile.setOverallScore(global);
        gitHubProfileRepository.save(profile);
        return global;
    }

    public GitHubProfileResponse getGitHubProfileForUser(UUID userId) {
        return toResponse(gitHubProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found for userId=" + userId)));
    }

    public boolean profileExistsForUser(UUID userId) {
        return gitHubProfileRepository.findByUserId(userId).isPresent();
    }

    public boolean profileExistsByGithubUsername(String githubUsername) {
        if (githubUsername == null) return false;
        return gitHubProfileRepository.findByGithubUsername(githubUsername.trim()).isPresent();
    }

    @Transactional
    public GitHubProfileResponse reauditProfile(UUID userId) {
        GitHubProfile profile = gitHubProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found for userId=" + userId));
        return auditGitHubProfile(userId, profile.getGithubUsername());
    }

    public GitHubProfileResponse findByUserId(UUID userId) {
        return toResponse(gitHubProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found for userId=" + userId)));
    }

    @Transactional
    public GitHubProfileResponse create(GitHubProfileRequest request) {
        validateUniqueUser(request.userId(), null);
        validateUniqueUsername(request.githubUsername(), null);
        GitHubProfile entity = new GitHubProfile();
        apply(entity, request);
        return toResponse(gitHubProfileRepository.save(entity));
    }

    @Transactional
    public GitHubProfileResponse update(UUID id, GitHubProfileRequest request) {
        GitHubProfile entity = requireEntity(id);
        validateUniqueUser(request.userId(), id);
        validateUniqueUsername(request.githubUsername(), id);
        apply(entity, request);
        return toResponse(gitHubProfileRepository.save(entity));
    }

    private void validateUniqueUser(UUID userId, UUID currentId) {
        gitHubProfileRepository.findByUserId(userId).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new DuplicateResourceException("A GitHubProfile already exists for userId=" + userId);
            }
        });
    }

    private void validateUniqueUsername(String githubUsername, UUID currentId) {
        gitHubProfileRepository.findByGithubUsername(githubUsername.trim()).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new DuplicateResourceException("A GitHubProfile already exists for githubUsername=" + githubUsername);
            }
        });
    }

    private void apply(GitHubProfile entity, GitHubProfileRequest request) {
        entity.setUserId(request.userId());
        entity.setGithubUsername(request.githubUsername().trim());
        entity.setOverallScore(request.overallScore());
        entity.setRepoCount(request.repoCount());
        entity.setTopLanguages(trimToNull(request.topLanguages()));
        entity.setProfileReadmeScore(request.profileReadmeScore());
        entity.setFeedback(trimToNull(request.feedback()));
        entity.setAuditStatus(request.auditStatus() != null ? request.auditStatus() : ProcessingStatus.PENDING);
        entity.setAuditErrorMessage(trimToNull(request.auditErrorMessage()));
        entity.setCreatedAt(request.createdAt() != null ? request.createdAt() : entity.getCreatedAt());
        entity.setAnalyzedAt(request.analyzedAt());
    }

    private int scoreReadmePresence(GitHubRepoRaw repo) {
        int score = 40;
        if (repo.description() != null && !repo.description().isBlank()) {
            score += Math.min(30, repo.description().length() / 4);
        }
        score += Math.min(30, repo.stargazersCount() / 3);
        return Math.min(score, 100);
    }

    private boolean detectCiCdSignals(GitHubRepoRaw repo) {
        String composite = (repo.name() + " " + (repo.description() == null ? "" : repo.description())).toLowerCase(Locale.ROOT);
        return composite.contains("ci") || composite.contains("cd") || composite.contains("pipeline") || composite.contains("workflow");
    }

    private boolean detectTestSignals(GitHubRepoRaw repo) {
        String composite = (repo.name() + " " + (repo.description() == null ? "" : repo.description())).toLowerCase(Locale.ROOT);
        return composite.contains("test") || composite.contains("spec") || composite.contains("qa");
    }

    private int scoreCodeStructure(GitHubRepoRaw repo, boolean hasCiCd, boolean hasTests) {
        int score = 35;
        if (repo.language() != null && !repo.language().isBlank()) {
            score += 20;
        }
        if (hasCiCd) {
            score += 20;
        }
        if (hasTests) {
            score += 20;
        }
        if (!repo.fork()) {
            score += 5;
        }
        return Math.min(score, 100);
    }

    private int computeRepositoryOverallScore(int readmeScore, boolean hasCiCd, boolean hasTests, int codeStructureScore, int stars) {
        int cicdScore = hasCiCd ? 100 : 35;
        int testsScore = hasTests ? 100 : 30;
        int starSignal = Math.min(100, stars * 4);
        return Math.min(100, (int) Math.round(
                (readmeScore * 0.30)
                        + (cicdScore * 0.20)
                        + (testsScore * 0.20)
                        + (codeStructureScore * 0.20)
                        + (starSignal * 0.10)
        ));
    }

    private String buildTopLanguagesJson(List<GitHubRepository> repositories) {
        if (repositories.isEmpty()) {
            return null;
        }

        Map<String, Long> languageCounts = repositories.stream()
                .map(GitHubRepository::getLanguage)
                .filter(language -> language != null && !language.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(language -> language, java.util.stream.Collectors.counting()));

        if (languageCounts.isEmpty()) {
            return null;
        }

        List<String> topLanguages = languageCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        try {
            return objectMapper.writeValueAsString(topLanguages);
        } catch (Exception e) {
            return String.join(",", topLanguages);
        }
    }

    private int calculateProfileReadmeScore(List<GitHubRepository> repositories) {
        return repositories.stream()
                .map(GitHubRepository::getReadmeScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .mapToInt(value -> (int) Math.round(value))
                .findFirst()
                .orElse(0);
    }

    private String buildAuditFeedback(List<GitHubRepository> repositories, Integer overallScore) {
        if (repositories.isEmpty()) {
            return "No public repositories found for audit.";
        }

        long lowScored = repositories.stream()
                .filter(repo -> repo.getOverallScore() != null && repo.getOverallScore() < 60)
                .count();

        long missingTests = repositories.stream()
                .filter(repo -> Boolean.FALSE.equals(repo.getHasTests()))
                .count();

        long missingCiCd = repositories.stream()
                .filter(repo -> Boolean.FALSE.equals(repo.getHasCiCd()))
                .count();

        return "Audited " + repositories.size() + " repositories. Overall score=" + (overallScore == null ? 0 : overallScore)
                + "; low-score repos=" + lowScored
                + "; repos missing tests=" + missingTests
                + "; repos missing CI/CD=" + missingCiCd + ".";
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record GitHubRepoRaw(
            String name,
            String htmlUrl,
            String language,
            int stargazersCount,
            int forksCount,
            boolean fork,
            Instant pushedAt,
            String description,
            boolean archived
    ) {
    }
}

