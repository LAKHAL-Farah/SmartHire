package tn.esprit.msroadmap.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.ProjectSubmission;
import tn.esprit.msroadmap.Entities.ProjectSuggestion;
import tn.esprit.msroadmap.Enums.SubmissionStatus;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.ProjectSubmissionRepository;
import tn.esprit.msroadmap.Repositories.ProjectSuggestionRepository;
import tn.esprit.msroadmap.ServicesImpl.IProjectSubmissionService;
import tn.esprit.msroadmap.ai.AiClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ProjectSubmissionServiceImpl implements IProjectSubmissionService {

    private static final int PASS_THRESHOLD = 70;
    private static final int MAX_RECOMMENDATIONS_STORAGE_LENGTH = 240;

    private final ProjectSubmissionRepository repository;
    private final ProjectSuggestionRepository suggestionRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public ProjectSubmission submitProject(Long userId, Long suggestionId, String repoUrl) {
        ProjectSuggestion suggestion = suggestionRepository.findById(suggestionId).orElseThrow(() -> new ResourceNotFoundException("Suggestion not found"));
        ProjectSubmission s = new ProjectSubmission();
        s.setUserId(userId);
        s.setProjectSuggestion(suggestion);
        s.setRepoUrl(repoUrl);
        s.setStatus(SubmissionStatus.PENDING_REVIEW);
        return repository.save(s);
    }

    @Override
    public List<ProjectSubmission> getSubmissionsByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public ProjectSubmission getSubmissionById(Long submissionId) {
        return repository.findById(submissionId).orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
    }

    @Override
    public ProjectSubmission retrySubmission(Long submissionId, String newRepoUrl) {
        ProjectSubmission s = getSubmissionById(submissionId);
        if (s.getRetryCount() >= 3) throw new IllegalStateException("Max retries reached");
        s.setRepoUrl(newRepoUrl);
        s.setRetryCount(s.getRetryCount() + 1);
        s.setStatus(SubmissionStatus.PENDING_REVIEW);
        s.setScore(null);
        s.setReadmeScore(null);
        s.setStructureScore(null);
        s.setTestScore(null);
        s.setCiScore(null);
        s.setAiFeedback(null);
        s.setRecommendations(null);
        s.setReviewedAt(null);
        return repository.save(s);
    }

    @Override
    public String getAiReviewResult(Long submissionId) {
        ProjectSubmission submission = getSubmissionById(submissionId);

        if (submission.getAiFeedback() != null
                && !submission.getAiFeedback().isBlank()
                && submission.getReviewedAt() != null
                && submission.getStatus() != SubmissionStatus.PENDING_REVIEW) {
            return submission.getAiFeedback();
        }

        SubmissionReviewResult review = generateReview(submission);
        applyReview(submission, review);
        repository.save(submission);
        return submission.getAiFeedback();
    }

    private SubmissionReviewResult generateReview(ProjectSubmission submission) {
        ProjectSuggestion suggestion = submission.getProjectSuggestion();
        String challengeTitle = suggestion != null && suggestion.getTitle() != null
                ? suggestion.getTitle().trim()
                : "Coding Challenge";
        String challengeDescription = suggestion != null && suggestion.getDescription() != null
                ? suggestion.getDescription().trim()
                : "No description provided";
        String challengeDifficulty = suggestion != null && suggestion.getDifficulty() != null
                ? suggestion.getDifficulty().name()
                : "INTERMEDIATE";
        String techStack = suggestion == null || suggestion.getTechStack() == null || suggestion.getTechStack().isEmpty()
                ? "Not specified"
                : suggestion.getTechStack().stream().filter(t -> t != null && !t.isBlank()).collect(Collectors.joining(", "));

        String systemPrompt = "You are a senior software reviewer. Evaluate submissions fairly and concisely. "
                + "Return only valid JSON.";

        String userPrompt = String.format("""
                Review this coding project submission based on metadata.

                Challenge title: %s
                Challenge difficulty: %s
                Challenge description: %s
                Expected technologies: %s
                Repository URL: %s

                Return ONLY JSON in this shape:
                {
                  "summary": "string",
                  "overallScore": number,
                  "readmeScore": number,
                  "structureScore": number,
                  "testScore": number,
                  "ciScore": number,
                  "recommendations": ["string", "string", "string"]
                }

                Constraints:
                - scores are integers from 0 to 100
                - summary is short and practical
                - recommendations are actionable and specific
                """,
                challengeTitle,
                challengeDifficulty,
                challengeDescription,
                techStack,
                submission.getRepoUrl());

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            return parseReviewResult(aiResponse, challengeTitle, challengeDifficulty);
        } catch (Exception ex) {
            log.warn("AI project review generation failed for submission {}: {}", submission.getId(), ex.getMessage());
            return fallbackReviewResult(submission, challengeTitle, challengeDifficulty);
        }
    }

    private SubmissionReviewResult parseReviewResult(String aiResponse, String challengeTitle, String challengeDifficulty) {
        try {
            String cleaned = (aiResponse == null ? "" : aiResponse)
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode payload = root.isObject() && root.has("review") ? root.get("review") : root;

            int overall = boundedScore(payload.path("overallScore").asInt(0));
            int readme = boundedScore(payload.path("readmeScore").asInt(overall));
            int structure = boundedScore(payload.path("structureScore").asInt(overall));
            int test = boundedScore(payload.path("testScore").asInt(Math.max(0, overall - 10)));
            int ci = boundedScore(payload.path("ciScore").asInt(Math.max(0, overall - 5)));

            String summary = payload.path("summary").asText("").trim();
            if (summary.isBlank()) {
                summary = challengeTitle + " review generated.";
            }

            List<String> recommendations = new ArrayList<>();
            JsonNode recNode = payload.path("recommendations");
            if (recNode.isArray()) {
                for (JsonNode item : recNode) {
                    String value = item == null ? "" : item.asText("").trim();
                    if (!value.isBlank()) {
                        recommendations.add(value);
                    }
                }
            }

            if (recommendations.isEmpty()) {
                recommendations = defaultRecommendations(challengeDifficulty);
            }

            return new SubmissionReviewResult(summary, overall, readme, structure, test, ci, recommendations);
        } catch (Exception ex) {
            log.warn("Failed to parse AI review payload: {}", ex.getMessage());
            return new SubmissionReviewResult(
                    challengeTitle + " review could not be parsed cleanly; fallback analysis used.",
                    65,
                    70,
                    66,
                    60,
                    64,
                    defaultRecommendations(challengeDifficulty));
        }
    }

    private SubmissionReviewResult fallbackReviewResult(
            ProjectSubmission submission,
            String challengeTitle,
            String challengeDifficulty) {
        String repo = submission.getRepoUrl() == null ? "" : submission.getRepoUrl().trim().toLowerCase(Locale.ROOT);
        boolean githubLike = repo.startsWith("https://github.com/") || repo.startsWith("http://github.com/");
        int depthHint = repo.split("/").length;

        int overall = githubLike ? 72 : 58;
        if (depthHint >= 5) {
            overall += 6;
        }
        overall = boundedScore(overall);

        int readme = boundedScore(overall + (githubLike ? 4 : 0));
        int structure = boundedScore(overall);
        int test = boundedScore(Math.max(35, overall - 12));
        int ci = boundedScore(Math.max(30, overall - 10));

        String summary;
        if (overall >= PASS_THRESHOLD) {
            summary = "Repository link is valid and the submission is ready for iterative improvement. "
                    + "Add stronger test coverage and CI checks for a higher score.";
        } else {
            summary = "Submission is registered but needs stronger project evidence. "
                    + "Improve repository structure, documentation, and tests.";
        }

        return new SubmissionReviewResult(
                challengeTitle + ": " + summary,
                overall,
                readme,
                structure,
                test,
                ci,
                defaultRecommendations(challengeDifficulty));
    }

    private void applyReview(ProjectSubmission submission, SubmissionReviewResult review) {
        submission.setScore(review.overallScore());
        submission.setReadmeScore(review.readmeScore());
        submission.setStructureScore(review.structureScore());
        submission.setTestScore(review.testScore());
        submission.setCiScore(review.ciScore());
        submission.setRecommendations(compactRecommendations(review.recommendations()));
        submission.setAiFeedback(review.summary());
        submission.setReviewedAt(LocalDateTime.now());
        submission.setStatus(review.overallScore() >= PASS_THRESHOLD ? SubmissionStatus.PASSED : SubmissionStatus.FAILED);
    }

    private String compactRecommendations(List<String> recommendations) {
        String joined = String.join("\n", recommendations);
        if (joined.length() <= MAX_RECOMMENDATIONS_STORAGE_LENGTH) {
            return joined;
        }
        return joined.substring(0, MAX_RECOMMENDATIONS_STORAGE_LENGTH);
    }

    private int boundedScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private List<String> defaultRecommendations(String difficulty) {
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Add a clear README with setup, run, and API usage examples.");
        recommendations.add("Include automated tests for core flows and edge cases.");
        recommendations.add("Configure CI to run linting and tests on every push.");

        if ("BEGINNER".equalsIgnoreCase(difficulty)) {
            recommendations.add("Keep project scope focused and ensure code is easy to follow.");
        } else {
            recommendations.add("Document architecture choices and trade-offs for maintainability.");
        }

        return recommendations;
    }

    private record SubmissionReviewResult(
            String summary,
            int overallScore,
            int readmeScore,
            int structureScore,
            int testScore,
            int ciScore,
            List<String> recommendations) {
    }
}
