package tn.esprit.msinterview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tn.esprit.msinterview.ai.NvidiaAiClient;
import tn.esprit.msinterview.ai.TTSClient;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Configuration
public class TestAiStubConfig {

    @Bean
    @Primary
    NvidiaAiClient nvidiaAiClient(ObjectMapper objectMapper) {
        NvidiaAiClient mock = Mockito.mock(NvidiaAiClient.class);

        Mockito.doAnswer(invocation -> {
            String userPrompt = invocation.getArgument(1, String.class);

            if (userPrompt.contains("Extract ML concepts")) {
                return json(objectMapper, """
                    {
                      "modelChosen": "XGBoost",
                      "features": ["encoding", "normalization"],
                      "metrics": ["F1", "AUC"],
                      "deployment": "FastAPI",
                      "dataPreprocessing": "normalization",
                      "evaluationStrategy": "cross-validation",
                      "missingConcepts": "monitoring details"
                    }
                    """);
            }

            if (userPrompt.contains("Evaluate this ML answer summary")) {
                return json(objectMapper, """
                    { "score": 8.4 }
                    """);
            }

            if (userPrompt.contains("Rubric (0-10 numbers)")) {
                return json(objectMapper, """
                    {
                      "codeCorrectness": 8.0,
                      "codeQuality": 7.8,
                      "algorithmicThinking": 8.2,
                      "explanationClarity": 7.6,
                      "depthOfKnowledge": 7.9,
                      "aiFeedback": "Solid implementation with clear reasoning.",
                      "complexityAssessment": "Time: O(n log n) Space: O(n)",
                      "followUpQuestion": "How would you optimize memory usage?"
                    }
                    """);
            }

            return json(objectMapper, """
                {
                  "relevance": 8.2,
                  "clarity": 7.9,
                  "technical": 8.4,
                  "communication": 7.7,
                  "aiFeedback": "Strong answer with practical deployment details.",
                  "followUpQuestion": "Can you justify the chosen model against alternatives?"
                }
                """);
        }).when(mock).chatJson(anyString(), anyString());

        return mock;
    }

    @Bean
    @Primary
    TTSClient ttsClient() {
        TTSClient mock = Mockito.mock(TTSClient.class);
        when(mock.preGenerateQuestionAudio(anyLong(), anyLong(), anyString())).thenReturn(null);
        when(mock.resolveQuestionAudioUrl(anyLong(), anyLong(), anyString())).thenReturn(null);
        return mock;
    }

    private static JsonNode json(ObjectMapper mapper, String payload) {
        try {
            return mapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse test JSON payload", ex);
        }
    }
}
