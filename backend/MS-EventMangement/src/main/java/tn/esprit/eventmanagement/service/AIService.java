package tn.esprit.eventmanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class AIService {

    @Value("${groq.api.key}")
    private String apiKey;

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final String MODEL = "llama3-8b-8192";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String evaluateProject(String title, String description) {
        String prompt = """
            You are a hackathon judge.
            Evaluate this project and return ONLY valid JSON with no extra text:

            {
              "originality": number (0-10),
              "technical": number (0-10),
              "feasibility": number (0-10),
              "feedback": "text"
            }

            Project Title: %s
            Description: %s
            """.formatted(title, description);

        return callGroqAI(prompt);
    }

    private String callGroqAI(String prompt) {
        try {
            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Build request body
            Map<String, Object> body = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "max_tokens", 500,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // Call Groq API
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    GROQ_API_URL, request, Map.class
            );

            // Extract response content
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices =
                        (List<Map<String, Object>>) response.getBody().get("choices");

                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message =
                            (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Groq API call failed: " + e.getMessage(), e);
        }

        return """
            {
              "originality": 0,
              "technical": 0,
              "feasibility": 0,
              "feedback": "Evaluation failed."
            }
            """;
    }
}