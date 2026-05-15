package tn.esprit.msjob.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tn.esprit.msjob.config.properties.NvidiaProperties;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class NvidiaAiService {

    private final WebClient nvidiaWebClient;
    private final NvidiaProperties nvidiaProperties;
    private final ObjectMapper objectMapper;

    record NimMessage(String role, String content) {
    }

    record NimRequest(
            String model,
            List<NimMessage> messages,
            double temperature,
            @JsonProperty("top_p") double topP,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream
    ) {
    }

    record NimChoice(int index, NimMessage message,
                     @JsonProperty("finish_reason") String finishReason) {
    }

    record NimUsage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }

    record NimResponse(String id, List<NimChoice> choices, NimUsage usage) {
    }

    public record AiResult(String content, int tokensUsed) {
    }

    public NvidiaAiService(
            @Qualifier("nvidiaWebClient") WebClient nvidiaWebClient,
            NvidiaProperties nvidiaProperties,
            ObjectMapper objectMapper
    ) {
        this.nvidiaWebClient = nvidiaWebClient;
        this.nvidiaProperties = nvidiaProperties;
        this.objectMapper = objectMapper;
    }

    public AiResult prompt(String systemPrompt, String userPrompt) {
        try {
            NimRequest request = new NimRequest(
                    nvidiaProperties.model(),
                    List.of(new NimMessage("system", systemPrompt), new NimMessage("user", userPrompt)),
                    nvidiaProperties.temperature(),
                    nvidiaProperties.topP(),
                    nvidiaProperties.maxTokens(),
                    false
            );

            String body = nvidiaWebClient
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(nvidiaProperties.timeoutSeconds()))
                    .block();

            if (body == null || body.isBlank()) {
                throw new RuntimeException("Empty response from NVIDIA NIM API");
            }

            NimResponse response = objectMapper.readValue(body, NimResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new RuntimeException("Empty response from NVIDIA NIM API");
            }

            String content = response.choices().get(0).message().content();
            int tokensUsed = response.usage() != null ? response.usage().totalTokens() : 0;
            return new AiResult(content, tokensUsed);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new RuntimeException("Invalid NVIDIA API key for MS_JOB", e);
            }
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("NVIDIA NIM rate limit exceeded", e);
            }
            throw new RuntimeException("NVIDIA NIM request failed with status " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new RuntimeException("NVIDIA NIM service unavailable", e);
        }
    }
}
