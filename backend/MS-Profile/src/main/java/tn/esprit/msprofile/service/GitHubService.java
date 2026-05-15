package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.config.AppConstants;
import tn.esprit.msprofile.config.properties.GitHubProperties;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.dto.response.GitHubRepoResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.GitHubRepository;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ExternalApiException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.exception.ValidationException;
import tn.esprit.msprofile.mapper.GitHubProfileMapper;
import tn.esprit.msprofile.mapper.GitHubRepoMapper;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.GitHubRepositoryRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {
    private static final int DB_ERROR_MESSAGE_MAX = 500;
    private static final int DB_REPO_DESCRIPTION_MAX = 512;
    private static final int MAX_DEEP_AUDITED_REPOS = 7;

    private final GitHubProfileRepository githubProfileRepository;
    private final GitHubRepositoryRepository githubRepositoryRepository;
    private final GitHubApiService gitHubApiService;
    private final NvidiaAiService nvidiaAiService;
    private final AuditLogService auditLogService;
    private final ProfileTipService profileTipService;
    private final GitHubProfileMapper githubProfileMapper;
    private final GitHubRepoMapper githubRepoMapper;
    private final ObjectMapper objectMapper;
    private final GitHubProperties gitHubProperties;

    @Transactional
    public GitHubProfileResponse auditProfile(String githubProfileUrl) {
        UUID userId = AppConstants.currentUserId();
        String username = extractUsername(githubProfileUrl);

        Optional<GitHubProfile> existing = githubProfileRepository.findByUserId(userId);
        GitHubProfile profile;
        if (existing.isPresent()) {
            profile = existing.get();
            githubRepositoryRepository.deleteByGithubProfileId(profile.getId());
            profile.setGithubUsername(username);
            profile.setProfileUrl("https://github.com/" + username);
            profile.setAuditStatus(ProcessingStatus.IN_PROGRESS);
            profile.setAuditErrorMessage(null);
            profile.setAnalyzedAt(null);
        } else {
            profile = new GitHubProfile();
            profile.setUserId(userId);
            profile.setGithubUsername(username);
            profile.setProfileUrl("https://github.com/" + username);
            profile.setAuditStatus(ProcessingStatus.IN_PROGRESS);
            profile.setCreatedAt(Instant.now());
        }
        profile = githubProfileRepository.saveAndFlush(profile);

        AuditLog auditLog = auditLogService.logOperation(
            userId,
                OperationType.GITHUB_AUDIT,
                "GitHubProfile",
                profile.getId(),
                ProcessingStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        long startTime = System.currentTimeMillis();
        int totalTokensUsed = 0;

        try {
            List<GitHubApiService.GhRepo> ghRepos = gitHubApiService.fetchPublicRepos(username);
            if (ghRepos.size() > MAX_DEEP_AUDITED_REPOS) {
                log.warn("GitHub profile {} has {} repos; deep audit will be capped at {} repos to keep the request responsive.",
                        username, ghRepos.size(), MAX_DEEP_AUDITED_REPOS);
            }
            if (ghRepos.size() > MAX_DEEP_AUDITED_REPOS) {
                ghRepos = ghRepos.subList(0, MAX_DEEP_AUDITED_REPOS);
            }
            profile.setRepoCount(ghRepos.size());

            Optional<String> profileReadme = gitHubApiService.fetchProfileReadme(username);
            int profileReadmeScore = 0;
            if (profileReadme.isPresent()) {
                NvidiaAiService.AiResult readmeResult = nvidiaAiService.scoreReadme(
                        username + "/" + username + " (Profile README)",
                        profileReadme.get()
                );
                totalTokensUsed += readmeResult.tokensUsed();
                JsonNode readmeJson = objectMapper.readTree(readmeResult.content());
                profileReadmeScore = readmeJson.path("score").asInt(0);
            }
            profile.setProfileReadmeScore(profileReadmeScore);
            githubProfileRepository.saveAndFlush(profile);

            List<GitHubRepository> savedRepos = new ArrayList<>();
            List<GitHubRepository> auditedRepos = new ArrayList<>();
            Map<String, Integer> languageCounts = new LinkedHashMap<>();

            for (int index = 0; index < ghRepos.size(); index++) {
                GitHubApiService.GhRepo ghRepo = ghRepos.get(index);
                GitHubRepository repo = new GitHubRepository();
                repo.setGithubProfile(profile);
                repo.setRepoName(ghRepo.name());
                repo.setRepoUrl(ghRepo.htmlUrl());
                repo.setDescription(truncate(ghRepo.description(), DB_REPO_DESCRIPTION_MAX));
                repo.setLanguage(ghRepo.language());
                repo.setStars(ghRepo.stargazersCount());
                repo.setForksCount(ghRepo.forksCount());
                repo.setIsForked(ghRepo.fork());
                repo.setIsArchived(ghRepo.archived());
                if (ghRepo.pushedAt() != null) {
                    try {
                        Instant pushedAt = Instant.parse(ghRepo.pushedAt());
                        repo.setPushedAt(pushedAt);
                        repo.setUpdatedAt(pushedAt);
                    } catch (Exception ignored) {
                        repo.setPushedAt(null);
                    }
                }

                if (ghRepo.language() != null) {
                    languageCounts.merge(ghRepo.language(), 1, Integer::sum);
                }

                if (index < MAX_DEEP_AUDITED_REPOS) {
                    try {
                        Optional<String> readme = gitHubApiService.fetchReadme(username, ghRepo.name());
                        if (readme.isPresent()) {
                            NvidiaAiService.AiResult readmeResult = nvidiaAiService.scoreReadme(ghRepo.name(), readme.get());
                            totalTokensUsed += readmeResult.tokensUsed();
                            JsonNode readmeJson = objectMapper.readTree(readmeResult.content());
                            repo.setReadmeScore(readmeJson.path("score").asInt(0));
                        } else {
                            repo.setReadmeScore(0);
                        }
                    } catch (Exception e) {
                        log.warn("README scoring failed for {}/{}: {}", username, ghRepo.name(), e.getMessage());
                        repo.setReadmeScore(0);
                    }

                    // CI/CD and test coverage checks are intentionally skipped for the lightweight audit mode.
                    repo.setHasCiCd(null);
                    repo.setHasTests(null);

                    try {
                        NvidiaAiService.AiResult auditResult = nvidiaAiService.auditRepository(
                                ghRepo.name(),
                                ghRepo.language() != null ? ghRepo.language() : "Unknown",
                                ghRepo.description() != null ? ghRepo.description() : "No description",
                                false,
                                false,
                                repo.getReadmeScore() != null ? repo.getReadmeScore() : 0,
                                ghRepo.stargazersCount(),
                                ghRepo.pushedAt() != null ? ghRepo.pushedAt() : "unknown"
                        );
                        totalTokensUsed += auditResult.tokensUsed();
                        JsonNode auditJson = objectMapper.readTree(auditResult.content());
                        repo.setOverallScore(auditJson.path("overallScore").asInt(0));
                        repo.setCodeStructureScore(auditJson.path("codeStructureScore").asInt(0));
                        String feedback = auditJson.path("feedback").asText(null);
                        if (feedback == null || feedback.isBlank()) {
                            feedback = buildFallbackAssessment(ghRepo.name(), repo.getReadmeScore(), ghRepo.pushedAt());
                        }
                        repo.setAuditFeedback(feedback);

                        JsonNode suggestionsNode = auditJson.path("fixSuggestions");
                        if (suggestionsNode.isArray() && !suggestionsNode.isEmpty()) {
                            repo.setFixSuggestions(objectMapper.writeValueAsString(suggestionsNode));
                        } else {
                            repo.setFixSuggestions(buildFallbackSuggestions(repo.getReadmeScore()));
                        }
                        repo.setDetectedIssues(feedback);
                    } catch (Exception e) {
                        log.warn("AI audit failed for {}/{}: {}", username, ghRepo.name(), e.getMessage());
                        int readmeScore = repo.getReadmeScore() == null ? 0 : repo.getReadmeScore();
                        int recencyScore = computeRecencyScore(ghRepo.pushedAt());
                        int overallScore = Math.min(100, Math.max(0, (int) Math.round(readmeScore * 0.7 + recencyScore * 0.3)));
                        repo.setOverallScore(overallScore);
                        repo.setCodeStructureScore(Math.max(20, Math.min(90, readmeScore / 2 + 30)));
                        repo.setAuditFeedback(buildFallbackAssessment(ghRepo.name(), repo.getReadmeScore(), ghRepo.pushedAt()));
                        repo.setFixSuggestions(buildFallbackSuggestions(repo.getReadmeScore()));
                        repo.setDetectedIssues(repo.getAuditFeedback());
                    }

                    auditedRepos.add(repo);
                }

                savedRepos.add(githubRepositoryRepository.saveAndFlush(repo));
            }

            List<String> topLanguages = languageCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            profile.setTopLanguages(objectMapper.writeValueAsString(topLanguages));

                List<GitHubRepository> scoredRepos = auditedRepos.isEmpty() ? savedRepos : auditedRepos;

                int avgReadmeScore = (int) Math.round(scoredRepos.stream()
                    .filter(r -> r.getReadmeScore() != null)
                    .mapToInt(GitHubRepository::getReadmeScore)
                    .average()
                    .orElse(0));

                int reposWithCiCd = 0;
                int reposWithTests = 0;

            Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);
                int recentRepos = (int) scoredRepos.stream()
                    .filter(r -> r.getPushedAt() != null && r.getPushedAt().isAfter(ninetyDaysAgo))
                    .count();

                int recencyScore = scoredRepos.isEmpty() ? 0 : (int) Math.round((double) recentRepos / scoredRepos.size() * 100);
                int ciCdScore = 0;
                int testScore = 0;

            int overallScore = (int) Math.round(
                    avgReadmeScore * 0.70
                        + recencyScore * 0.30
            );
            overallScore = Math.min(100, Math.max(0, overallScore));
            profile.setOverallScore(overallScore);

            try {
                NvidiaAiService.AiResult feedbackResult = nvidiaAiService.generateGitHubProfileFeedback(
                        username,
                        overallScore,
                        ghRepos.size(),
                        topLanguages,
                        reposWithCiCd,
                        reposWithTests,
                        avgReadmeScore,
                        profileReadme.isPresent()
                );
                totalTokensUsed += feedbackResult.tokensUsed();
                profile.setFeedback(feedbackResult.content());
            } catch (Exception e) {
                log.warn("Profile feedback generation failed: {}", e.getMessage());
            }

            profile.setAuditStatus(ProcessingStatus.COMPLETED);
            profile.setAnalyzedAt(Instant.now());
            profile = githubProfileRepository.saveAndFlush(profile);

            long durationMs = System.currentTimeMillis() - startTime;
            auditLogService.updateLog(auditLog.getId(), ProcessingStatus.COMPLETED, totalTokensUsed, (int) durationMs, null);

            try {
                profileTipService.generateTipsForGitHub(userId, profile.getId());
            } catch (Exception e) {
                log.warn("GitHub tip generation failed: {}", e.getMessage());
            }

            return githubProfileMapper.toResponse(profile, savedRepos);
        } catch (Exception exception) {
            profile.setAuditStatus(ProcessingStatus.FAILED);
            profile.setAuditErrorMessage(truncate(exception.getMessage(), DB_ERROR_MESSAGE_MAX));
            // Protect error path from failing a second time due persistence constraints.
            try {
                githubProfileRepository.save(profile);
            } catch (Exception persistenceError) {
                log.error("Failed to persist FAILED status for GitHub profile {}: {}",
                        profile.getId(),
                        persistenceError.getMessage());
            }
            try {
                auditLogService.updateLog(
                        auditLog.getId(),
                        ProcessingStatus.FAILED,
                        null,
                        (int) (System.currentTimeMillis() - startTime),
                        exception.getMessage()
                );
            } catch (Exception ignored) {
                // Preserve original failure.
            }
            List<GitHubRepository> repositories = profile.getId() == null
                    ? List.of()
                    : githubRepositoryRepository.findByGithubProfileIdOrderByOverallScoreDesc(profile.getId());
            return githubProfileMapper.toResponse(profile, repositories);
        }
    }

    @Transactional(readOnly = true)
    public GitHubProfileResponse getProfile() {
        UUID userId = AppConstants.currentUserId();
        GitHubProfile profile = githubProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    GitHubProfile empty = new GitHubProfile();
                    empty.setUserId(userId);
                    empty.setAuditStatus(ProcessingStatus.PENDING);
                    empty.setCreatedAt(Instant.now());
                    return empty;
                });

        if (profile.getId() == null) {
            return githubProfileMapper.toResponse(profile, List.of());
        }

        List<GitHubRepository> repositories = githubRepositoryRepository
                .findByGithubProfileIdOrderByOverallScoreDesc(profile.getId());
        return githubProfileMapper.toResponse(profile, repositories);
    }

    @Transactional
    public GitHubProfileResponse reauditProfile(String githubProfileUrl) {
        return auditProfile(githubProfileUrl);
    }

    @Transactional(readOnly = true)
    public List<GitHubRepoResponse> getRepositories() {
        UUID userId = AppConstants.currentUserId();
        GitHubProfile profile = githubProfileRepository.findByUserId(userId)
                .orElse(null);

        if (profile == null || profile.getId() == null) {
            return List.of();
        }

        List<GitHubRepository> repositories = githubRepositoryRepository
                .findByGithubProfileIdOrderByOverallScoreDesc(profile.getId());
        return githubRepoMapper.toResponseList(repositories);
    }

    private String extractUsername(String input) {
        if (input == null || input.isBlank()) {
            throw new ValidationException("GitHub profile URL cannot be empty");
        }
        String cleaned = input.trim()
                .replaceAll("https?://", "")
                .replaceAll("^www\\.", "")
                .replaceAll("^github\\.com/", "")
                .replaceAll("/$", "")
                .split("[/?#]")[0];

        if (!cleaned.matches("[a-zA-Z0-9][a-zA-Z0-9\\-]{0,38}")) {
            throw new ValidationException(
                    "Invalid GitHub profile URL or username: '" + input + "'. Expected format: https://github.com/username or just 'username'"
            );
        }
        return cleaned;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private int computeRecencyScore(String pushedAt) {
        if (pushedAt == null || pushedAt.isBlank()) {
            return 35;
        }
        try {
            Instant pushed = Instant.parse(pushedAt);
            long days = ChronoUnit.DAYS.between(pushed, Instant.now());
            if (days <= 30) {
                return 100;
            }
            if (days <= 90) {
                return 75;
            }
            if (days <= 180) {
                return 55;
            }
            if (days <= 365) {
                return 35;
            }
            return 15;
        } catch (Exception ignored) {
            return 35;
        }
    }

    private String buildFallbackAssessment(String repoName, Integer readmeScore, String pushedAt) {
        int score = readmeScore == null ? 0 : readmeScore;
        int recency = computeRecencyScore(pushedAt);
        return "Automated assessment for " + repoName + ": README score " + score
                + "/100, activity score " + recency
                + "/100. Improve README clarity and keep regular updates to raise overall quality.";
    }

    private String buildFallbackSuggestions(Integer readmeScore) {
        int score = readmeScore == null ? 0 : readmeScore;
        List<String> suggestions = new ArrayList<>();
        if (score < 60) {
            suggestions.add("Improve README structure with clear setup and usage sections.");
        }
        suggestions.add("Add practical examples so recruiters can quickly understand the project value.");
        suggestions.add("Keep commits regular to signal active maintenance.");
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (Exception ignored) {
            return "[]";
        }
    }
}
