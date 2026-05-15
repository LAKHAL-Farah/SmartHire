package tn.esprit.msprofile.e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractE2ETest {

    protected static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final String BASE_URL = "http://localhost:8092";
    protected static final int WIREMOCK_PORT = 9999;

    static WireMockServer wireMock;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WIREMOCK_PORT);
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    protected void stubNvidiaSuccess(String contentJson) {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matching("(?s).*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(nvidiaEnvelope(contentJson))));
    }

    protected void stubNvidiaParseSuccess() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(containing("expert CV parser"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(nvidiaEnvelope(TestFixtures.mockParsedCvJson()))));
    }

    protected void stubNvidiaKeywordsSuccess() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(containing("expert technical recruiter"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(nvidiaEnvelope(TestFixtures.mockJobKeywordsJson()))));
    }

    protected void stubNvidiaTailorSuccess() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(containing("expert CV writer and ATS optimization specialist"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(nvidiaEnvelope(TestFixtures.mockTailoredCvJson()))));
    }

    protected void stubNvidiaScoreSuccess() {
        String analysisJson = """
                {
                  "overallScore": 82,
                  "breakdown": {
                    "keywordMatch": 85,
                    "experienceRelevance": 80,
                    "skillsAlignment": 82,
                    "educationCertifications": 80
                  },
                  "matchedKeywords": ["Java", "Spring Boot", "Angular", "Docker", "MySQL"],
                  "missingKeywords": ["Kubernetes", "Kafka"],
                  "strengthSummary": "Strong full-stack background with relevant Java and Angular experience",
                  "improvementSuggestions": [
                    "Add Kubernetes experience to experience descriptions",
                    "Mention Kafka or message broker experience if applicable"
                  ]
                }
                """;
            wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("ATS (Applicant Tracking System) scoring engine"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(nvidiaEnvelope(analysisJson))));
    }

    protected String nvidiaEnvelope(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", "nimcmpl-test-001",
                    "object", "chat.completion",
                    "model", "meta/llama-3.3-70b-instruct",
                    "choices", new Object[]{Map.of(
                            "index", 0,
                            "message", Map.of("role", "assistant", "content", content),
                            "finish_reason", "stop"
                    )},
                    "usage", Map.of(
                            "prompt_tokens", 150,
                            "completion_tokens", 300,
                            "total_tokens", 450
                    )
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build NVIDIA envelope", e);
        }
    }
}
