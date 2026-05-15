package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.NvidiaAiClient;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.ArchitectureDiagram;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.repository.AnswerEvaluationRepository;
import tn.esprit.msinterview.repository.ArchitectureDiagramRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.ArchitectureDiagramService;
import tn.esprit.msinterview.websocket.SessionEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchitectureDiagramServiceImpl implements ArchitectureDiagramService {

    private final ArchitectureDiagramRepository architectureDiagramRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final AnswerEvaluationRepository answerEvaluationRepository;
    private final ObjectMapper objectMapper;
    private final NvidiaAiClient aiClient;
    private final SessionEventPublisher sessionEventPublisher;

    private record DiagramStats(int nodeCount, int edgeCount, List<String> componentTypes) {}

    private record RequirementCoverage(List<String> met, List<String> missed) {}

    private static final Map<String, List<String>> REQUIREMENT_ALIASES = Map.of(
            "health_checks", List.of("health_check", "monitoring"),
            "deployment_strategy", List.of("blue_green", "canary", "load_balancer"),
            "result_storage", List.of("object_storage", "storage"),
            "db_replication", List.of("database_replica", "replica", "database"),
            "auto_failover", List.of("failover", "proxy", "dns_failover"),
            "auto_scaling", List.of("autoscaling", "compute", "vm", "container", "kubernetes")
    );

    private static final int SCENARIO_MAX_CHARS = 500;
    private static final int REQUIREMENTS_MAX_CHARS = 1000;
    private static final int CRITERIA_MAX_CHARS = 700;
    private static final int EXPLANATION_MAX_CHARS = 900;
    private static final int COMPONENTS_MAX_CHARS = 350;

    @Override
    @Transactional
    public ArchitectureDiagram submitDiagram(Long answerId, Long sessionId, Long questionId, String diagramJson) {
        SessionAnswer answer = sessionAnswerRepository.findById(answerId)
                .orElseThrow(() -> new IllegalArgumentException("Answer not found: " + answerId));

        if (sessionId != null && answer.getSession() != null && !sessionId.equals(answer.getSession().getId())) {
            throw new IllegalArgumentException("Session mismatch for answer " + answerId);
        }
        if (questionId != null && answer.getQuestion() != null && !questionId.equals(answer.getQuestion().getId())) {
            throw new IllegalArgumentException("Question mismatch for answer " + answerId);
        }

        DiagramStats stats = parseDiagramStats(diagramJson);
    Set<String> componentTypes = new LinkedHashSet<>(stats.componentTypes());
    JsonNode metadata = answer.getQuestion() != null
        ? parseJson(answer.getQuestion().getMetadata())
        : objectMapper.createObjectNode();
    RequirementCoverage initialCoverage = computeRequirementCoverage(metadata.path("requirements"), componentTypes);
    double initialScore = initialCoverage.met().isEmpty() && initialCoverage.missed().isEmpty()
        ? 0.0
        : round2((initialCoverage.met().size() * 10.0)
        / (initialCoverage.met().size() + initialCoverage.missed().size()));

        ArchitectureDiagram diagram = architectureDiagramRepository.findByAnswerId(answerId)
                .orElse(ArchitectureDiagram.builder().answer(answer).build());

        diagram.setDiagramJson(diagramJson);
        diagram.setNodeCount(stats.nodeCount());
        diagram.setEdgeCount(stats.edgeCount());
        diagram.setComponentTypes(writeJson(stats.componentTypes()));
    diagram.setDesignScore(initialScore);
    diagram.setRequirementsMet(writeJson(initialCoverage.met()));
    diagram.setRequirementsMissed(writeJson(initialCoverage.missed()));
    diagram.setAiFeedback("Strengths: Core components detected in the diagram.\n\nWeaknesses: Some required components may be missing while AI evaluation is still running.\n\nRecommendations: Add missing requirements and explain trade-offs for reliability, scale, and cost.");
    diagram.setFollowUpGenerated("Can you explain the trade-offs behind your selected components?");
    diagram.setEvaluatedAt(LocalDateTime.now());

        return architectureDiagramRepository.save(diagram);
    }

    @Override
    @Transactional
    public ArchitectureDiagram explainDiagram(Long diagramId, Long sessionId, Long questionId, String explanation) {
        ArchitectureDiagram diagram = architectureDiagramRepository.findById(diagramId)
                .orElseThrow(() -> new IllegalArgumentException("Diagram not found: " + diagramId));

        SessionAnswer answer = diagram.getAnswer();
        if (answer == null) {
            throw new IllegalArgumentException("Diagram " + diagramId + " is not linked to an answer");
        }

        if (sessionId != null && answer.getSession() != null && !sessionId.equals(answer.getSession().getId())) {
            throw new IllegalArgumentException("Session mismatch for diagram " + diagramId);
        }
        if (questionId != null && answer.getQuestion() != null && !questionId.equals(answer.getQuestion().getId())) {
            throw new IllegalArgumentException("Question mismatch for diagram " + diagramId);
        }

        answer.setAnswerText(explanation == null ? "" : explanation.trim());
        sessionAnswerRepository.save(answer);
        return diagram;
    }

    @Override
    @Transactional(readOnly = true)
    public ArchitectureDiagram getDiagramByAnswer(Long answerId) {
        return architectureDiagramRepository.findByAnswerId(answerId)
                .orElseThrow(() -> new IllegalArgumentException("Diagram not found for answer: " + answerId));
    }

    @Override
    @Async
    @Transactional
    public void evaluateDiagram(Long diagramId) {
        ArchitectureDiagram diagram = architectureDiagramRepository.findById(diagramId)
                .orElseThrow(() -> new IllegalArgumentException("Diagram not found: " + diagramId));

        SessionAnswer answer = diagram.getAnswer();
        if (answer == null || answer.getQuestion() == null) {
            throw new IllegalArgumentException("Diagram is not linked to a valid answer/question");
        }

        InterviewQuestion question = answer.getQuestion();
        Long sessionId = answer.getSession() != null ? answer.getSession().getId() : null;

        JsonNode metadata = parseJson(question.getMetadata());
        JsonNode requirementsNode = metadata.path("requirements");
        JsonNode criteriaNode = metadata.path("evaluationCriteria");
        String scenario = truncate(metadata.path("scenario").asText(question.getQuestionText()), SCENARIO_MAX_CHARS);
        Set<String> componentTypes = new LinkedHashSet<>(parseComponentTypes(diagram.getComponentTypes()));

        RequirementCoverage fallbackCoverage = computeRequirementCoverage(requirementsNode, componentTypes);
        double fallbackScore = fallbackCoverage.met().isEmpty() && fallbackCoverage.missed().isEmpty()
                ? 0.0
                : round2((fallbackCoverage.met().size() * 10.0) / (fallbackCoverage.met().size() + fallbackCoverage.missed().size()));

        try {
            String systemPrompt = "You are a senior cloud architect. Evaluate the design and return ONLY JSON.";

            String componentsLine = componentTypes.isEmpty()
                    ? "(none)"
                    : truncate(String.join(", ", componentTypes), COMPONENTS_MAX_CHARS);

            String explanation = answer.getAnswerText() == null || answer.getAnswerText().isBlank()
                    ? "No explanation provided."
                    : truncate(answer.getAnswerText(), EXPLANATION_MAX_CHARS);

            String requirementsSummary = truncate(numberedRequirements(requirementsNode), REQUIREMENTS_MAX_CHARS);
            String criteriaSummary = truncate(numberedList(criteriaNode), CRITERIA_MAX_CHARS);

            String userPrompt = """
                SCENARIO:
                %s

                REQUIREMENTS:
                %s

                CANDIDATE DIAGRAM:
                Components used: %s
                Connections: %d connections drawn
                Total components placed: %d

                CANDIDATE EXPLANATION:
                %s

                EVALUATION CRITERIA:
                %s

                Score 0-10 for:
                - completeness: Are all required components present?
                - scalability: Can this design handle 10x traffic?
                - reliability: Are there single points of failure?
                - security: Is the design properly secured?
                - costEfficiency: Is this reasonably cost-efficient?
                - explanationClarity: Does the explanation clearly justify component choices and show trade-offs?

                requirementsMet and requirementsMissed must contain requirement keys only.

                Also provide:
                - overallScore: weighted average (completeness 40%%, others 12%% each)
                - strengths: max 220 chars
                - weaknesses: max 220 chars
                - recommendations: max 220 chars
                - followUpQuestion: one probing question about their design choices

                Return ONLY this JSON:
                {
                  \"completeness\": 7.0,
                  \"scalability\": 8.0,
                  \"reliability\": 6.0,
                  \"security\": 5.0,
                  \"costEfficiency\": 7.0,
                  \"explanationClarity\": 7.5,
                  \"overallScore\": 6.8,
                  \"requirementsMet\": [\"load_balancer\",\"database\",\"object_storage\"],
                  \"requirementsMissed\": [\"monitoring\",\"cdn\"],
                  \"strengths\": \"Good use of load balancing and separate database layer.\",
                  \"weaknesses\": \"No monitoring present. Security layer missing.\",
                  \"recommendations\": \"Add a monitoring service and a firewall or security group.\",
                  \"followUpQuestion\": \"How would your design change if traffic doubled overnight?\"
                }
                """.formatted(
                    scenario,
                    requirementsSummary,
                    componentsLine,
                    diagram.getEdgeCount() == null ? 0 : diagram.getEdgeCount(),
                    diagram.getNodeCount() == null ? 0 : diagram.getNodeCount(),
                    explanation,
                    criteriaSummary
            );

            JsonNode result = askAiJson(systemPrompt, userPrompt);

            List<String> requirementsMet = toStringList(result.path("requirementsMet"));
            List<String> requirementsMissed = toStringList(result.path("requirementsMissed"));
            if (requirementsMet.isEmpty() && requirementsMissed.isEmpty()) {
                requirementsMet = fallbackCoverage.met();
                requirementsMissed = fallbackCoverage.missed();
            }

            double overallScore = safeDouble(result, "overallScore", fallbackScore);
            String strengths = safeText(result, "strengths", "Design contains several useful cloud building blocks.");
            String weaknesses = safeText(result, "weaknesses", "Some required components are missing or weakly justified.");
            String recommendations = safeText(result, "recommendations", "Add missing required components and improve resilience.");
            String followUp = safeText(result, "followUpQuestion", "How would you evolve this design for a 10x traffic increase?");

            diagram.setDesignScore(round2(overallScore));
            diagram.setRequirementsMet(writeJson(requirementsMet));
            diagram.setRequirementsMissed(writeJson(requirementsMissed));
            diagram.setAiFeedback("Strengths: " + strengths
                    + "\n\nWeaknesses: " + weaknesses
                    + "\n\nRecommendations: " + recommendations);
            diagram.setFollowUpGenerated(followUp);
            diagram.setEvaluatedAt(LocalDateTime.now());

            ArchitectureDiagram saved = architectureDiagramRepository.save(diagram);

            Double explanationClarity = result.has("explanationClarity")
                    ? safeDouble(result, "explanationClarity", 0.0)
                    : null;
            updateExplanationClarity(answer, explanationClarity, followUp);

            sessionEventPublisher.pushEvaluationReady(sessionId, DTOMapper.toArchitectureDTO(saved));
        } catch (Exception ex) {
            log.warn("Diagram AI evaluation failed for diagram {}: {}", diagramId, ex.getMessage());

            diagram.setDesignScore(fallbackScore);
            diagram.setRequirementsMet(writeJson(fallbackCoverage.met()));
            diagram.setRequirementsMissed(writeJson(fallbackCoverage.missed()));
            diagram.setAiFeedback("Strengths: Core components detected in the diagram.\n\nWeaknesses: AI evaluation failed, fallback scoring applied.\n\nRecommendations: Re-run evaluation and verify requirement coverage.");
            diagram.setFollowUpGenerated("Can you explain the trade-offs behind your selected components?");
            diagram.setEvaluatedAt(LocalDateTime.now());

            ArchitectureDiagram saved = architectureDiagramRepository.save(diagram);
                updateExplanationClarity(
                    answer,
                    estimateExplanationClarity(answer.getAnswerText()),
                    diagram.getFollowUpGenerated()
                );
            sessionEventPublisher.pushEvaluationReady(sessionId, DTOMapper.toArchitectureDTO(saved));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchitectureDiagram> getDiagramsBySession(Long sessionId) {
        return architectureDiagramRepository.findByAnswerSessionId(sessionId);
    }

    private DiagramStats parseDiagramStats(String diagramJson) {
        if (diagramJson == null || diagramJson.isBlank()) {
            return new DiagramStats(0, 0, List.of());
        }

        try {
            JsonNode diagramNode = objectMapper.readTree(diagramJson);
            JsonNode nodes = diagramNode.path("nodes");
            JsonNode edges = diagramNode.path("edges");

            int nodeCount = nodes.isArray() ? nodes.size() : 0;
            int edgeCount = edges.isArray() ? edges.size() : 0;

            Set<String> types = new LinkedHashSet<>();
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    String type = normalizeKey(node.path("type").asText(""));
                    if (type.isBlank()) {
                        type = normalizeKey(node.path("componentType").asText(""));
                    }
                    if (type.isBlank()) {
                        type = normalizeKey(node.path("label").asText(""));
                    }
                    if (!type.isBlank()) {
                        types.add(type);
                    }
                }
            }

            JsonNode components = diagramNode.path("components");
            if (components.isArray()) {
                for (JsonNode component : components) {
                    String type = normalizeKey(component.asText(""));
                    if (!type.isBlank()) {
                        types.add(type);
                    }
                }
            }

            return new DiagramStats(nodeCount, edgeCount, new ArrayList<>(types));
        } catch (Exception ex) {
            log.warn("Failed to parse diagram JSON: {}", ex.getMessage());
            return new DiagramStats(0, 0, List.of());
        }
    }

    private List<String> parseComponentTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode entry : node) {
                    String normalized = normalizeKey(entry.asText(""));
                    if (!normalized.isBlank()) {
                        values.add(normalized);
                    }
                }
                return values;
            }
        } catch (Exception ignored) {
            // Fallback to comma-separated parsing.
        }

        return List.of(raw.split(","))
                .stream()
                .map(this::normalizeKey)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private RequirementCoverage computeRequirementCoverage(JsonNode requirementsNode, Set<String> components) {
        List<String> met = new ArrayList<>();
        List<String> missed = new ArrayList<>();

        if (!requirementsNode.isArray()) {
            return new RequirementCoverage(met, missed);
        }

        for (JsonNode requirement : requirementsNode) {
            String key = normalizeKey(requirement.path("key").asText(""));
            String label = normalizeKey(requirement.path("label").asText(""));
            String canonical = key.isBlank() ? label : key;
            if (canonical.isBlank()) {
                continue;
            }

            if (matchesRequirement(canonical, components)) {
                met.add(canonical);
            } else {
                missed.add(canonical);
            }
        }

        return new RequirementCoverage(met, missed);
    }

    private boolean matchesRequirement(String requirementKey, Set<String> components) {
        if (components.contains(requirementKey)) {
            return true;
        }

        List<String> aliases = REQUIREMENT_ALIASES.getOrDefault(requirementKey, List.of());
        for (String alias : aliases) {
            String normalizedAlias = normalizeKey(alias);
            if (normalizedAlias.isBlank()) {
                continue;
            }

            for (String component : components) {
                if (component.equals(normalizedAlias)
                        || component.contains(normalizedAlias)
                        || normalizedAlias.contains(component)) {
                    return true;
                }
            }
        }

        for (String component : components) {
            if (component.contains(requirementKey) || requirementKey.contains(component)) {
                return true;
            }
        }

        return false;
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            log.warn("Failed to parse metadata JSON: {}", ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String numberedRequirements(JsonNode requirementsNode) {
        if (!requirementsNode.isArray() || requirementsNode.isEmpty()) {
            return "1. No explicit requirements provided.";
        }

        List<String> lines = new ArrayList<>();
        int index = 1;
        for (JsonNode requirement : requirementsNode) {
            String label = requirement.path("label").asText("").trim();
            String key = requirement.path("key").asText("").trim();
            boolean required = requirement.path("required").asBoolean(true);

            String summary = label.isBlank() ? key : label;
            if (!key.isBlank() && !summary.equals(key)) {
                summary = summary + " (key: " + key + ")";
            }

            lines.add(index + ". " + summary + " | required=" + required);
            index++;
        }
        return String.join("\n", lines);
    }

    private String numberedList(JsonNode items) {
        if (!items.isArray() || items.isEmpty()) {
            return "1. No explicit criteria provided.";
        }

        List<String> lines = new ArrayList<>();
        int index = 1;
        for (JsonNode item : items) {
            String text = item.asText("").trim();
            if (text.isBlank()) {
                continue;
            }
            lines.add(index + ". " + text);
            index++;
        }
        if (lines.isEmpty()) {
            return "1. No explicit criteria provided.";
        }
        return String.join("\n", lines);
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }

        return toDistinctNormalized(node);
    }

    private List<String> toDistinctNormalized(JsonNode node) {
        return node.findValuesAsText("")
                .stream()
                .map(this::normalizeKey)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private double safeDouble(JsonNode node, String field, double defaultValue) {
        JsonNode candidate = node.path(field);
        if (candidate.isNumber()) {
            return candidate.asDouble();
        }
        if (candidate.isTextual()) {
            try {
                return Double.parseDouble(candidate.asText().trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String safeText(JsonNode node, String field, String defaultValue) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? defaultValue : value;
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private void updateExplanationClarity(SessionAnswer answer, Double explanationClarity, String followUpQuestion) {
        if (answer == null || answer.getId() == null || explanationClarity == null) {
            return;
        }

        AnswerEvaluation evaluation = answerEvaluationRepository.findByAnswerId(answer.getId())
                .orElseGet(() -> AnswerEvaluation.builder().answer(answer).build());

        evaluation.setClarityScore(round2(explanationClarity));
        if (evaluation.getOverallScore() == null) {
            evaluation.setOverallScore(round2(explanationClarity));
        }
        if (followUpQuestion != null && !followUpQuestion.isBlank()) {
            evaluation.setFollowUpGenerated(followUpQuestion.trim());
        }

        answerEvaluationRepository.save(evaluation);
    }

    private Double estimateExplanationClarity(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return 0.0;
        }

        int words = explanation.trim().split("\\s+").length;
        if (words >= 70) {
            return 8.0;
        }
        if (words >= 45) {
            return 7.0;
        }
        if (words >= 25) {
            return 6.0;
        }
        if (words >= 10) {
            return 5.0;
        }
        return 4.0;
    }

    private JsonNode askAiJson(String systemPrompt, String userPrompt) {
        try {
            return aiClient.chatJson(systemPrompt, userPrompt);
        } catch (Exception firstError) {
            String raw = aiClient.chat(systemPrompt, userPrompt);
            String cleaned = raw
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode direct = tryParseJson(cleaned);
            if (direct != null) {
                return direct;
            }

            int objectStart = cleaned.indexOf('{');
            int objectEnd = cleaned.lastIndexOf('}');
            if (objectStart >= 0 && objectEnd > objectStart) {
                JsonNode extracted = tryParseJson(cleaned.substring(objectStart, objectEnd + 1));
                if (extracted != null) {
                    return extracted;
                }
            }

            throw firstError;
        }
    }

    private JsonNode tryParseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...[truncated]";
    }
}
