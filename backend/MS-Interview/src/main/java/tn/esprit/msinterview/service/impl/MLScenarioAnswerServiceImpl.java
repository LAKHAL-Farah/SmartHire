package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.NvidiaAiClient;
import tn.esprit.msinterview.entity.MLScenarioAnswer;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.repository.MLScenarioAnswerRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.MLScenarioAnswerService;
import tn.esprit.msinterview.websocket.SessionEventPublisher;

import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MLScenarioAnswerServiceImpl implements MLScenarioAnswerService {

    private final MLScenarioAnswerRepository mlScenarioAnswerRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final NvidiaAiClient aiClient;
    private final SessionEventPublisher sessionEventPublisher;
    private final ConcurrentHashMap<Long, Object> extractionLocks = new ConcurrentHashMap<>();

    @Override
    @Async
    @Transactional
    public void extractAndSaveAsync(Long answerId, String transcript) {
        extractAndSave(answerId, transcript);
    }

    @Override
    @Transactional
    public MLScenarioAnswer extractAndSave(Long answerId, String transcript) {
        Object lock = extractionLocks.computeIfAbsent(answerId, key -> new Object());
        synchronized (lock) {
            try {
                SessionAnswer answer = sessionAnswerRepository.findById(answerId)
                        .orElseThrow(() -> new IllegalArgumentException("Answer not found: " + answerId));

                String safeTranscript = transcript != null ? transcript : "";
                MLScenarioAnswer mlAnswer = mlScenarioAnswerRepository.findByAnswerId(answerId)
                        .orElse(MLScenarioAnswer.builder().answer(answer).build());

                try {
                    JsonNode extracted = extractConceptsWithNvidia(safeTranscript);
                    mlAnswer.setModelChosen(nonBlank(extracted.path("modelChosen").asText(null), "Not provided"));
                    mlAnswer.setFeaturesDescribed(nonBlank(nodeToText(extracted.path("features")), "Not provided"));
                    mlAnswer.setMetricsDescribed(nonBlank(nodeToText(extracted.path("metrics")), "Not provided"));
                    mlAnswer.setDeploymentStrategy(nonBlank(preferredText(extracted, "deployment", "deploymentStrategy"), "Not provided"));
                    mlAnswer.setDataPreprocessing(nonBlank(extracted.path("dataPreprocessing").asText(null), "Not provided"));
                    mlAnswer.setEvaluationStrategy(nonBlank(extracted.path("evaluationStrategy").asText(null), "Not provided"));
                    mlAnswer.setExtractedConcepts(nonBlank(extracted.path("missingConcepts").asText(null), "No major gaps detected."));
                } catch (Exception ex) {
                    log.warn("ML extraction failed for answer {}: {}", answerId, ex.getMessage());
                    mlAnswer.setModelChosen("Not provided");
                    mlAnswer.setFeaturesDescribed("Not provided");
                    mlAnswer.setMetricsDescribed("Not provided");
                    mlAnswer.setDeploymentStrategy("Not provided");
                    mlAnswer.setDataPreprocessing("Not provided");
                    mlAnswer.setEvaluationStrategy("Not provided");
                    mlAnswer.setExtractedConcepts(safeTranscript.substring(0, Math.min(200, safeTranscript.length())));
                }

                MLScenarioAnswer saved = mlScenarioAnswerRepository.save(mlAnswer);

                String followUp = generateFollowUp(answerId);
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                payload.put("answerId", answerId);
                payload.put("followUpGenerated", followUp);
                sessionEventPublisher.pushFollowUp(answer.getSession().getId(), payload);

                scoreMLAnswer(answerId);
                return saved;
            } finally {
                extractionLocks.remove(answerId, lock);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MLScenarioAnswer getByAnswer(Long answerId) {
        return mlScenarioAnswerRepository.findByAnswerId(answerId)
                .orElseThrow(() -> new IllegalArgumentException("ML scenario answer not found for answer: " + answerId));
    }

    @Override
    @Transactional(readOnly = true)
    public String generateFollowUp(Long answerId) {
        MLScenarioAnswer mlAnswer = getByAnswer(answerId);
        String modelChosen = mlAnswer.getModelChosen();
        if (hasValue(modelChosen)) {
            String alternative = alternativeModelFor(modelChosen);
            return "You chose " + modelChosen.trim() + " - why over " + alternative + "?";
        }

        if (!hasValue(mlAnswer.getMetricsDescribed())) {
            return "What metrics would you use to evaluate this model?";
        }

        if (!hasValue(mlAnswer.getDeploymentStrategy())) {
            return "How would you deploy this model to production?";
        }

        if (!hasValue(mlAnswer.getDataPreprocessing())) {
            return "How would you preprocess the raw data before training?";
        }

        return "What trade-offs would you revisit if this model underperformed in production?";
    }

    @Override
    @Async
    @Transactional
    public void scoreMLAnswer(Long answerId) {
        MLScenarioAnswer mlAnswer = mlScenarioAnswerRepository.findByAnswerId(answerId)
                .orElseThrow(() -> new IllegalArgumentException("ML scenario answer not found for answer: " + answerId));

        double score = 7.0;
        try {
            String systemPrompt = "You are an ML interviewer. Return only JSON.";
            String userPrompt = """
                Evaluate this ML answer summary on a 0-10 scale.

                modelChosen: %s
                features: %s
                metrics: %s
                deployment: %s
                dataPreprocessing: %s
                evaluationStrategy: %s
                gaps: %s

                Return only JSON:
                { "score": 7.5 }
                """.formatted(
                    mlAnswer.getModelChosen(),
                    mlAnswer.getFeaturesDescribed(),
                    mlAnswer.getMetricsDescribed(),
                    mlAnswer.getDeploymentStrategy(),
                        mlAnswer.getDataPreprocessing(),
                        mlAnswer.getEvaluationStrategy(),
                    mlAnswer.getExtractedConcepts()
            );

            JsonNode result = aiClient.chatJson(systemPrompt, userPrompt);
            score = result.path("score").asDouble(7.0);
        } catch (Exception ex) {
            log.warn("ML scoring failed for answer {}: {}", answerId, ex.getMessage());
        }

        mlAnswer.setAiEvaluationScore(round2(score));
        mlScenarioAnswerRepository.save(mlAnswer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MLScenarioAnswer> getBySession(Long sessionId) {
        return mlScenarioAnswerRepository.findByAnswerSessionId(sessionId);
    }

    private JsonNode extractConceptsWithNvidia(String transcript) {
        String systemPrompt = "You are an ML expert. Extract structured concepts and return only JSON.";
        String userPrompt = """
            Extract ML concepts from this candidate answer.

            Transcript: %s

            Return ONLY this JSON:
            {
              "modelChosen": "...",
              "features": ["..."],
              "metrics": ["..."],
                            "deployment": "...",
                            "dataPreprocessing": "...",
                            "evaluationStrategy": "...",
              "missingConcepts": "..."
            }
            """.formatted(transcript);

        return aiClient.chatJson(systemPrompt, userPrompt);
    }

    private static String nodeToText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(node.get(i).asText());
            }
            return sb.toString();
        }
        return node.asText();
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String preferredText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank() && !"Not provided".equalsIgnoreCase(value.trim());
    }

    private static String alternativeModelFor(String chosenModel) {
        String lower = chosenModel.toLowerCase();
        if (lower.contains("xgboost")) {
            return "Random Forest";
        }
        if (lower.contains("random forest")) {
            return "XGBoost";
        }
        if (lower.contains("logistic")) {
            return "XGBoost";
        }
        if (lower.contains("svm")) {
            return "Logistic Regression";
        }
        if (lower.contains("neural") || lower.contains("transformer")) {
            return "a gradient-boosted tree model";
        }
        return "a strong baseline model";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

}
