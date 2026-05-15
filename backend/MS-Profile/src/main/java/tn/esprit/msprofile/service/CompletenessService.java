package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msprofile.dto.CompletenessResult;

@Service
@RequiredArgsConstructor
public class CompletenessService {

    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    public CompletenessResult computeCompleteness(String cvJson) {
        OpenAiService.AiResult result = openAiService.scoreCompletenessWithAi(cvJson);
        try {
            return objectMapper.readValue(result.content(), CompletenessResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse completeness analysis", e);
        }
    }

    public String serializeCompleteness(CompletenessResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize completeness analysis", e);
        }

    }

    public CompletenessResult deserializeCompleteness(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CompletenessResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize completeness analysis", e);
        }
    }
}
