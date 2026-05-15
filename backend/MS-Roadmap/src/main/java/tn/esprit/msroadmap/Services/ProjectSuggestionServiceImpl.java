package tn.esprit.msroadmap.Services;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.ProjectSuggestion;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import tn.esprit.msroadmap.Enums.DifficultyLevel;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.ProjectSuggestionRepository;
import tn.esprit.msroadmap.Repositories.RoadmapStepRepository;
import tn.esprit.msroadmap.ServicesImpl.IProjectSuggestionService;
import tn.esprit.msroadmap.ai.AiClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
@Slf4j
public class ProjectSuggestionServiceImpl implements IProjectSuggestionService {

    private final ProjectSuggestionRepository repository;
    private final RoadmapStepRepository stepRepository;
    private final AiClient aiClient;

    @Override
    public List<ProjectSuggestion> getSuggestionsByStepId(Long stepId) {
        return repository.findByStepIdOrderByCreatedAtDescIdDesc(stepId);
    }

    @Override
    public List<ProjectSuggestion> getSuggestionsByDifficulty(DifficultyLevel difficulty) {
        return repository.findByDifficulty(difficulty);
    }

    @Override
    public List<ProjectSuggestion> browseSuggestions(DifficultyLevel difficulty, String tech, int page, int size) {
        // naive implementation: filter by difficulty and then paginate in memory
        var all = repository.findByDifficulty(difficulty);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    @Override
    public List<ProjectSuggestion> generateProjectSuggestions(Long stepId, String domain, String level) {
        if (stepId == null || stepId <= 0) {
            throw new BusinessException("stepId must be a positive number");
        }

        RoadmapStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("Step not found"));

        String safeDomain = (domain == null || domain.isBlank()) ? "general software" : domain.trim();
        String safeLevel = (level == null || level.isBlank()) ? "INTERMEDIATE" : level.trim().toUpperCase(Locale.ROOT);
    DifficultyLevel requestedDifficulty = parseDifficulty(safeLevel);

        String systemPrompt = "You are an expert project generator for learning roadmaps. "
                + "Generate practical, real-world project ideas that help learners apply their skills. "
                + "Respond ONLY with valid JSON array. No markdown, no explanation.";

        String userPrompt = String.format("""
                Generate 3 project ideas for a learner who just completed:
                Topic: %s
                Domain: %s
                Difficulty level: %s

                Return JSON array with this exact structure:
                [
                  {
                    "title": "Project name",
                    "description": "What the learner will build (2-3 sentences)",
                    "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED",
                    "techStack": ["tech1", "tech2", "tech3"],
                                        "estimatedDays": number,
                    "githubTopics": ["topic1", "topic2"]
                  }
                ]
                """, step.getTitle(), safeDomain, safeLevel);

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            List<ProjectSuggestion> aiSuggestions = parseAndPersistAiSuggestions(
                    aiResponse,
                    step,
                    safeDomain,
                    requestedDifficulty);

            if (!aiSuggestions.isEmpty()) {
                return aiSuggestions;
            }

            log.warn("AI returned no usable project suggestions for step {}. Using fallback templates.", stepId);
        } catch (Exception ex) {
            log.warn("AI project suggestion generation failed for step {}. Using fallback templates. Cause: {}",
                    stepId,
                    ex.getMessage());
        }

        return generateFallbackSuggestions(step, safeDomain, requestedDifficulty);
    }

    @Override
    public List<ProjectSuggestion> generateProjectSuggestionsByRoadmapStep(
            Long roadmapId,
            Integer stepOrder,
            String domain,
            String level) {
        if (roadmapId == null || roadmapId <= 0) {
            throw new BusinessException("roadmapId must be a positive number");
        }
        if (stepOrder == null || stepOrder <= 0) {
            throw new BusinessException("stepOrder must be a positive number");
        }

        RoadmapStep step = stepRepository.findByRoadmapIdAndStepOrder(roadmapId, stepOrder);
        if (step == null) {
            throw new ResourceNotFoundException(
                    "Roadmap step not found for roadmapId=" + roadmapId + " and stepOrder=" + stepOrder);
        }

        return generateProjectSuggestions(step.getId(), domain, level);
    }

    @Override
    public List<ProjectSuggestion> getSuggestionsByRoadmapStep(Long roadmapId, Integer stepOrder) {
        if (roadmapId == null || roadmapId <= 0) {
            throw new BusinessException("roadmapId must be a positive number");
        }
        if (stepOrder == null || stepOrder <= 0) {
            throw new BusinessException("stepOrder must be a positive number");
        }

        return repository.findByStepRoadmapIdAndStepStepOrderOrderByCreatedAtDescIdDesc(roadmapId, stepOrder);
    }

    private List<ProjectSuggestion> parseAndPersistAiSuggestions(
            String aiResponse,
            RoadmapStep step,
            String safeDomain,
            DifficultyLevel requestedDifficulty) throws Exception {
        String cleaned = (aiResponse == null ? "" : aiResponse)
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        if (cleaned.isBlank()) {
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(cleaned);
        JsonNode projects = root;
        if (root != null && root.isObject()) {
            if (root.has("projects")) {
                projects = root.get("projects");
            } else if (root.has("suggestions")) {
                projects = root.get("suggestions");
            }
        }

        if (projects == null || !projects.isArray() || projects.isEmpty()) {
            return List.of();
        }

        List<ProjectSuggestion> suggestions = new ArrayList<>();
        int index = 0;
        for (JsonNode p : projects) {
            ProjectSuggestion suggestion = new ProjectSuggestion();
            suggestion.setStep(step);
            suggestion.setTitle(asText(p, "title", "Project " + (index + 1) + " - " + step.getTitle()));
            suggestion.setDescription(asText(p, "description", "Apply " + step.getTitle() + " in a practical build."));
            suggestion.setDifficulty(parseDifficulty(asText(p, "difficulty", requestedDifficulty.name())));
            suggestion.setEstimatedDays(asInt(p, "estimatedDays", fallbackEstimatedDays(requestedDifficulty, index)));

            List<String> techStack = new ArrayList<>();
            JsonNode techStackNode = p.get("techStack");
            if (techStackNode != null && techStackNode.isArray()) {
                for (JsonNode t : techStackNode) {
                    String value = t.asText("").trim();
                    if (!value.isBlank()) {
                        techStack.add(value);
                    }
                }
            }
            if (techStack.isEmpty()) {
                techStack = fallbackTechStack(step.getTitle(), safeDomain);
            }
            suggestion.setTechStack(techStack);

            List<String> topics = new ArrayList<>();
            JsonNode topicsNode = p.get("githubTopics");
            if (topicsNode != null && topicsNode.isArray()) {
                for (JsonNode t : topicsNode) {
                    String value = t.asText("").trim();
                    if (!value.isBlank()) {
                        topics.add(value);
                    }
                }
            }
            if (topics.isEmpty()) {
                topics = fallbackGithubTopics(step.getTitle(), safeDomain, index);
            }
            suggestion.setGithubTopics(topics);

            suggestion.setAlignedCareerPath(safeDomain);
            suggestion.setCreatedAt(LocalDateTime.now());
            suggestions.add(suggestion);
            index += 1;
        }

        if (suggestions.isEmpty()) {
            return List.of();
        }

        return repository.saveAll(suggestions);
    }

    private List<ProjectSuggestion> generateFallbackSuggestions(
            RoadmapStep step,
            String safeDomain,
            DifficultyLevel requestedDifficulty) {
        List<ProjectSuggestion> cached = repository.findByStepIdOrderByCreatedAtDescIdDesc(step.getId());
        if (!cached.isEmpty()) {
            log.info("Returning {} cached project suggestions for step {} while AI is unavailable.",
                    Math.min(cached.size(), 3),
                    step.getId());
            return cached.stream().limit(3).toList();
        }

        String topic = (step.getTitle() == null || step.getTitle().isBlank())
                ? "Core Topic"
                : step.getTitle().trim();

        List<ProjectSuggestion> fallback = new ArrayList<>();
        fallback.add(buildFallbackSuggestion(
                step,
                topic + " Foundations Lab",
                "Build a guided implementation that demonstrates the essential " + topic
                        + " workflow, including validation and documentation.",
                requestedDifficulty,
                fallbackEstimatedDays(requestedDifficulty, 0),
                fallbackTechStack(topic, safeDomain),
                fallbackGithubTopics(topic, safeDomain, 0),
                safeDomain));

        fallback.add(buildFallbackSuggestion(
                step,
                safeDomain + " Workflow Builder",
                "Create an end-to-end mini product for " + safeDomain
                        + " where " + topic + " is used for the core business flow.",
                requestedDifficulty,
                fallbackEstimatedDays(requestedDifficulty, 1),
                fallbackTechStack(topic, safeDomain),
                fallbackGithubTopics(topic, safeDomain, 1),
                safeDomain));

        fallback.add(buildFallbackSuggestion(
                step,
                topic + " Quality & Monitoring Sprint",
                "Ship a production-style project focused on testing, observability, and refactoring around "
                        + topic + ".",
                requestedDifficulty,
                fallbackEstimatedDays(requestedDifficulty, 2),
                fallbackTechStack(topic, safeDomain),
                fallbackGithubTopics(topic, safeDomain, 2),
                safeDomain));

        log.info("Generated {} fallback project suggestions for step {}.", fallback.size(), step.getId());
        return repository.saveAll(fallback);
    }

    private ProjectSuggestion buildFallbackSuggestion(
            RoadmapStep step,
            String title,
            String description,
            DifficultyLevel difficulty,
            int estimatedDays,
            List<String> techStack,
            List<String> githubTopics,
            String safeDomain) {
        ProjectSuggestion suggestion = new ProjectSuggestion();
        suggestion.setStep(step);
        suggestion.setTitle(title);
        suggestion.setDescription(description);
        suggestion.setDifficulty(difficulty);
        suggestion.setEstimatedDays(estimatedDays);
        suggestion.setTechStack(techStack);
        suggestion.setGithubTopics(githubTopics);
        suggestion.setAlignedCareerPath(safeDomain);
        suggestion.setCreatedAt(LocalDateTime.now());
        return suggestion;
    }

    private int fallbackEstimatedDays(DifficultyLevel difficulty, int variantIndex) {
        int offset = Math.max(0, variantIndex);
        return switch (difficulty) {
            case BEGINNER -> 4 + offset;
            case ADVANCED -> 9 + offset * 2;
            default -> 6 + offset;
        };
    }

    private List<String> fallbackTechStack(String topic, String safeDomain) {
        LinkedHashSet<String> stack = new LinkedHashSet<>();
        if (topic != null && !topic.isBlank()) {
            stack.add(topic.trim());
        }
        if (safeDomain != null && !safeDomain.isBlank()) {
            stack.add(safeDomain.trim());
        }
        stack.add("Git");
        stack.add("Testing");
        stack.add("Documentation");
        return new ArrayList<>(stack).stream().limit(5).toList();
    }

    private List<String> fallbackGithubTopics(String topic, String safeDomain, int variantIndex) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        String topicSlug = toGithubTopic(topic);
        if (!topicSlug.isBlank()) {
            topics.add(topicSlug);
        }

        String domainSlug = toGithubTopic(safeDomain);
        if (!domainSlug.isBlank()) {
            topics.add(domainSlug);
        }

        topics.add("portfolio-project");

        if (variantIndex % 3 == 0) {
            topics.add("learning-by-building");
        } else if (variantIndex % 3 == 1) {
            topics.add("workflow-automation");
        } else {
            topics.add("quality-engineering");
        }

        return new ArrayList<>(topics).stream().limit(5).toList();
    }

    private String toGithubTopic(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private DifficultyLevel parseDifficulty(String value) {
        try {
            return DifficultyLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return DifficultyLevel.INTERMEDIATE;
        }
    }

    private String asText(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            return fallback;
        }
        return n.asText();
    }

    private int asInt(JsonNode node, String field, int fallback) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) {
            return fallback;
        }
        return n.asInt(fallback);
    }
}
