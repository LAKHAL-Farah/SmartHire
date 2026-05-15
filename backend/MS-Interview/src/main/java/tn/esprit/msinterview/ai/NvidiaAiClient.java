package tn.esprit.msinterview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
public class NvidiaAiClient {

    @Value("${nvidia.api.key}")
    private String apiKey;

    @Value("${nvidia.api.base-url}")
    private String baseUrl;

    @Value("${nvidia.api.model}")
    private String model;

    @Value("${nvidia.api.max-tokens}")
    private int maxTokens;

    @Value("${nvidia.api.temperature}")
    private double temperature;

    @Value("${nvidia.api.top-p:0.7}")
    private double topP;

    @Value("${nvidia.api.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${nvidia.api.read-timeout-ms:45000}")
    private int readTimeoutMs;

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Core method — sends a prompt, returns the raw response string.
     * All other methods in this class call this one.
     */
    public String chat(String systemPrompt, String userPrompt) {
        log.debug("NvidiaAiClient.chat() model={} promptLength={}", model, userPrompt.length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("top_p", topP);
        body.put("stream", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/chat/completions", request, String.class
            );

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new RuntimeException("NVIDIA AI returned an empty response body");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("NVIDIA AI response does not contain choices");
            }

            String content = root
                .path("choices").get(0)
                .path("message")
                .path("content")
                .asText();

            if (content == null || content.isBlank()) {
                throw new RuntimeException("NVIDIA AI returned empty message content");
            }

            log.debug("NvidiaAiClient response received, length={}", content.length());
            return content;

        } catch (Exception e) {
            log.error("NvidiaAiClient.chat() failed: {}", e.getMessage());
            throw new RuntimeException("NVIDIA AI call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method — parses the response as JSON directly.
     * Use this for all evaluation calls where you need structured output.
     */
    public JsonNode chatJson(String systemPrompt, String userPrompt) {
        String raw = chat(systemPrompt, userPrompt);
        // Strip markdown fences if model wraps JSON in ```json ... ```
        String cleaned = raw
            .replaceAll("(?s)```json\\s*", "")
            .replaceAll("(?s)```\\s*", "")
            .trim();
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.error("Failed to parse AI JSON response: {}", cleaned);
            throw new RuntimeException("AI returned invalid JSON: " + cleaned, e);
        }
    }
}