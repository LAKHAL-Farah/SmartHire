package tn.esprit.eventmanagement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprit.eventmanagement.entities.Event;

import java.util.List;
import java.util.Map;

@Service
public class AiSummaryService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    public String generateSummary(Event event) {
        String prompt = buildPrompt(event);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", "llama-3.3-70b-versatile",
                "max_tokens", 500,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(GROQ_URL, request, Map.class);
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.getBody().get("choices");
            Map<String, Object> message =
                    (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            return "Summary generation failed: " + e.getMessage();
        }
    }

    private String buildPrompt(Event event) {
        return String.format("""
            Generate a professional and engaging summary for the following event.
            Include: agenda highlights, value for attendees, and key information.
            Keep it concise (3-4 sentences max). Write in English.

            Event details:
            - Title: %s
            - Type: %s
            - Description: %s
            - Location: %s
            - Start Date: %s
            - End Date: %s
            - Capacity: %d attendees
            - Online: %s
            """,
                event.getTitle(),
                event.getType(),
                event.getDescription()  != null ? event.getDescription()  : "N/A",
                event.getLocation()     != null ? event.getLocation()      : "N/A",
                event.getStartDate(),
                event.getEndDate(),
                event.getMaxCapacity()  != null ? event.getMaxCapacity()   : 0,
                Boolean.TRUE.equals(event.getOnline()) ? "Yes" : "No"
        );
    }
}