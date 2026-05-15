package tn.esprit.msassessment.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.esprit.msassessment.dto.ai.GeneratedQuestion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates assessment questions using the Groq cloud API (free tier).
 * Get your API key at https://console.groq.com
 */
@Component
@Slf4j
public class OllamaClient {

    @Value("${smarthire.groq.enabled:true}")
    private boolean enabled;

    @Value("${smarthire.groq.api-key:}")
    private String apiKey;

    @Value("${smarthire.groq.model:llama-3.1-8b-instant}")
    private String model;

    private final ObjectMapper objectMapper;

    public OllamaClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GeneratedQuestion> generateQuestions(String categoryTitle, String categoryDescription, int count) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.warn("[Groq] Disabled or API key not set. Set smarthire.groq.api-key in application.properties.");
            return new ArrayList<>();
        }
        try {
            log.info("[Groq] Generating {} questions for '{}'", count, categoryTitle);
            String prompt = buildPrompt(categoryTitle, count);
            String raw = callGroq(prompt);
            List<GeneratedQuestion> result = parseResponse(raw);
            log.info("[Groq] Generated {} valid questions", result.size());
            return result;
        } catch (Exception e) {
            log.error("[Groq] Failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public boolean isAvailable() {
        boolean available = enabled && apiKey != null && !apiKey.isBlank();
        if (available) log.info("[Groq] Available (model: {})", model);
        else log.warn("[Groq] Not available — check smarthire.groq.enabled and smarthire.groq.api-key");
        return available;
    }

    // ── Private ────────────────────────────────────────────────────────────

    private String buildPrompt(String categoryTitle, int count) {
        return String.format(
                "Generate %d multiple-choice questions about \"%s\". " +
                "Output a single flat JSON array only. " +
                "Each element: {\"prompt\":\"...\",\"choices\":[\"answer1\",\"answer2\",\"answer3\",\"answer4\"],\"correctIndex\":0,\"difficulty\":\"MEDIUM\"}. " +
                "choices must be full answer phrases, not letters A/B/C/D. " +
                "correctIndex is 0-3. difficulty is EASY, MEDIUM, or HARD. " +
                "No markdown, no explanation, no nested arrays. Just the flat JSON array.",
                count, categoryTitle);
    }

    private String callGroq(String prompt) throws Exception {
        String escapedPrompt = objectMapper.writeValueAsString(prompt);
        String requestBody = String.format("""
                {
                  "model": "%s",
                  "messages": [{"role": "user", "content": %s}],
                  "temperature": 0.7,
                  "max_tokens": 4096
                }
                """, model, escapedPrompt);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(8000));
        factory.setReadTimeout(Duration.ofMillis(60000));

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.groq.com")
                .build();

        String response = client.post()
                .uri("https://api.groq.com/openai/v1/chat/completions")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        if (response == null) throw new RuntimeException("Empty response from Groq");

        JsonNode root = objectMapper.readTree(response);
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private List<GeneratedQuestion> parseResponse(String response) {
        List<GeneratedQuestion> questions = new ArrayList<>();
        try {
            String jsonStr = extractJsonArray(response);
            JsonNode[] nodes = objectMapper.readValue(jsonStr, JsonNode[].class);

            for (JsonNode node : nodes) {
                try {
                    if (!node.has("prompt")) continue;

                    String prompt = node.get("prompt").asText();
                    JsonNode choicesNode = node.get("choices");
                    List<String> choices = new ArrayList<>();

                    if (choicesNode != null && choicesNode.isArray()) {
                        for (JsonNode choice : choicesNode) {
                            String text = choice.asText().trim();
                            if (text.length() > 1) choices.add(text);
                        }
                    }

                    int correctIndex = node.has("correctIndex") ? node.get("correctIndex").asInt(0) : 0;
                    String difficulty = node.has("difficulty") ? node.get("difficulty").asText("MEDIUM") : "MEDIUM";
                    if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) difficulty = "MEDIUM";

                    if (choices.size() == 4 && correctIndex >= 0 && correctIndex < 4) {
                        questions.add(new GeneratedQuestion(prompt, choices, correctIndex, difficulty));
                    } else {
                        log.warn("[Groq] Skipping question with {} choices", choices.size());
                    }
                } catch (Exception e) {
                    log.warn("[Groq] Skipping malformed node: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[Groq] Failed to parse response: {}", e.getMessage(), e);
        }
        return questions;
    }

    private String extractJsonArray(String response) {
        String cleaned = response
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) return cleaned.substring(start, end + 1);
        throw new RuntimeException("No JSON array in response: " + response.substring(0, Math.min(200, response.length())));
    }
}
