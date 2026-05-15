package tn.esprit.msroadmap.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tn.esprit.msroadmap.DTO.request.ChatMessageDto;
import tn.esprit.msroadmap.DTO.request.NvidiaChatRequestDto;
import tn.esprit.msroadmap.DTO.response.NvidiaChatResponseDto;
import tn.esprit.msroadmap.Exception.ServiceUnavailableException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiClient {

    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${nvidia.model.id:nvidia/nemotron-3-super-120b-a12b}")
    private String modelId;

    @Value("${nvidia.max-tokens:4096}")
    private int maxTokens;

    @Value("${nvidia.temperature:0.3}")
    private double temperature;

    @Value("${nvidia.reasoning-budget:4096}")
    private int reasoningBudget;

    public AiClient(@Qualifier("aiWebClient") WebClient aiWebClient, ObjectMapper objectMapper) {
        this.aiWebClient = aiWebClient;
        this.objectMapper = objectMapper;
    }

    public String call(String systemPrompt, String userPrompt) {
        try {
          Map<String, Object> extraBody = Map.of(
            "chat_template_kwargs", Map.of("enable_thinking", true),
            "reasoning_budget", reasoningBudget
          );

            NvidiaChatRequestDto request = new NvidiaChatRequestDto(
                    modelId,
                    List.of(
                            new ChatMessageDto("system", systemPrompt),
                            new ChatMessageDto("user", userPrompt)
                    ),
                    temperature,
                    maxTokens,
            0.95,
            extraBody
            );

          log.debug("Calling NVIDIA Nemotron API with model: {}", modelId);

            NvidiaChatResponseDto response = aiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NvidiaChatResponseDto.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ServiceUnavailableException("Empty response from NVIDIA API");
            }

            String content = response.choices().get(0).message().content();
            log.debug("NVIDIA API response received, length: {} chars", content == null ? 0 : content.length());

            return content;

        } catch (Exception e) {
            log.error("NVIDIA API call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("NVIDIA AI service unavailable: " + e.getMessage());
        }
    }

    // Convenience method for roadmap generation with pre-configured prompts
    public String generateRoadmap(String careerPath, String skillGaps, String strongSkills,
                                  String experienceLevel, int weeklyHours) {
        String systemPrompt = """
            You are an expert learning path designer with agentic reasoning capabilities.
            Use your reasoning to plan logical learning progressions.
            Output ONLY valid JSON. No markdown, no explanation, no code blocks.
            """;

        String userPrompt = String.format("""
            Create a structured visual learning roadmap for: %s

            User skill level: %s
            Skill gaps to address: %s
            Strong skills: %s
            Weekly hours available: %d

            Use your reasoning to plan a logical learning progression.

            Return ONLY valid JSON matching this schema:
            {
              "title": "string",
              "description": "string",
              "totalWeeks": number,
              "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED",
              "nodes": [
                {
                  "id": "node-1",
                  "title": "string",
                  "description": "string",
                  "objective": "string",
                  "type": "REQUIRED|OPTIONAL|ALTERNATIVE",
                  "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED",
                  "stepOrder": number,
                  "estimatedDays": number,
                  "technologies": "string",
                  "x": number,
                  "y": number
                }
              ],
              "edges": [
                {
                  "from": "node-1",
                  "to": "node-2",
                  "type": "REQUIRED|RECOMMENDED|OPTIONAL"
                }
              ],
              "projects": [
                {
                  "title": "string",
                  "description": "string",
                  "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED",
                  "estimatedDays": number,
                  "techStack": ["string"]
                }
              ]
            }

            Requirements:
            - Create 6-10 nodes with logical dependencies
            - Position nodes for top-down flow layout (x: 0-1200, y: 0-1000)
            - Ensure JSON is valid and parseable
            """, careerPath, experienceLevel, skillGaps, strongSkills, weeklyHours);

        return call(systemPrompt, userPrompt);
    }
}
