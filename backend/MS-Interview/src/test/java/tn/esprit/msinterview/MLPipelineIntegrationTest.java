package tn.esprit.msinterview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.msinterview.service.MLScenarioAnswerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestAiStubConfig.class)
class MLPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MLScenarioAnswerService mlScenarioAnswerService;

    @Test
    void seedVerification_mlPipelineCounts() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT type, role_type, domain, COUNT(*) AS cnt
            FROM interview_questions
            WHERE role_type = 'AI' AND domain = 'ml_pipeline'
            GROUP BY type, role_type, domain
            ORDER BY type, domain
            """);

        assertThat(rows).hasSize(2);

        Map<String, Number> countsByType = new HashMap<>();
        for (Map<String, Object> row : rows) {
            countsByType.put(String.valueOf(row.get("type")), (Number) row.get("cnt"));
        }

        assertThat(countsByType).containsEntry("TECHNICAL", 8L);
        assertThat(countsByType).containsEntry("SITUATIONAL", 2L);
    }

    @Test
    void seedVerification_noSampleCodeForNonCoding() {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM interview_questions
            WHERE role_type = 'AI'
              AND domain = 'ml_pipeline'
              AND sample_code IS NOT NULL
              AND TRIM(sample_code) <> ''
            """, Integer.class);

        assertThat(count).isNotNull();
        assertThat(count).isZero();
    }

    @Test
    void seedVerification_allMlPipelineQuestionsActive() {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM interview_questions
            WHERE role_type = 'AI'
              AND domain = 'ml_pipeline'
              AND is_active = true
            """, Integer.class);

        assertThat(count).isNotNull();
        assertThat(count).isEqualTo(10);
    }

    @Test
    void startAISession_returnsMlPipelineQuestion() throws Exception {
        JsonNode started = startAiPracticeTechnicalSession();

        assertThat(started.path("id").asLong()).isGreaterThan(0L);
        assertThat(started.path("roleType").asText()).isEqualTo("AI");
        assertThat(started.path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getFirstQuestion_isFromMLPipelineBank() throws Exception {
        JsonNode started = startAiPracticeTechnicalSession();
        long sessionId = started.path("id").asLong();

        JsonNode question = getCurrentQuestion(sessionId);

        assertThat(question.path("roleType").asText()).isEqualTo("AI");
        assertThat(question.path("expectedPoints").asText()).isNotBlank();

        String domain = question.path("domain").asText("");
        String type = question.path("type").asText("");
        assertThat(domain.equals("ml_pipeline") || type.equals("TECHNICAL") || type.equals("SITUATIONAL"))
                .isTrue();
    }

        @Test
        void aiTechnicalSession_includesMixedMlPipelineAndStandardTechnicalQuestions() throws Exception {
        JsonNode started = startAiPracticeTechnicalSession();
        long sessionId = started.path("id").asLong();

        String body = mockMvc.perform(get("/api/v1/sessions/{sessionId}/questions", sessionId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode ordered = objectMapper.readTree(body);
        List<String> technicalDomains = StreamSupport.stream(ordered.spliterator(), false)
            .map(node -> node.path("question"))
            .filter(question -> "TECHNICAL".equals(question.path("type").asText()))
            .map(question -> question.path("domain").asText(""))
            .toList();

        assertThat(technicalDomains)
            .as("AI technical session should include at least one ml_pipeline and one standard technical domain")
            .anyMatch(domain -> "ml_pipeline".equalsIgnoreCase(domain))
            .anyMatch(domain -> !"ml_pipeline".equalsIgnoreCase(domain));
        }

    @Test
    void submitTextAnswer_triggersMlExtraction() throws Exception {
        FlowContext flow = createAnswerFlow("AI");
        assertThat(flow.answerId).isGreaterThan(0L);

        JsonNode mlAnswer = pollMlAnswer(flow.answerId, 10, 2000);
        assertThat(mlAnswer.path("modelChosen").asText()).isNotBlank();
    }

    @Test
    void triggerMlExtraction_returnsAccepted() throws Exception {
        FlowContext flow = createAnswerFlow("SE");

        String payload = objectMapper.writeValueAsString(Map.of(
                "transcript",
                "I would use XGBoost for this classification task. I would encode categorical features and normalize numerical ones."
        ));

        String responseBody = mockMvc.perform(post("/api/v1/ml-answers/extract/{answerId}", flow.answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        assertThat(response.path("answerId").asLong()).isEqualTo(flow.answerId);
    }

    @Test
    void pollMlAnswer_conceptsExtracted() throws Exception {
        FlowContext flow = createAnswerFlow("SE");
        triggerExtraction(flow.answerId);

        JsonNode mlAnswer = pollMlAnswer(flow.answerId, 10, 2000);

        assertThat(mlAnswer).isNotNull();
        assertThat(mlAnswer.path("modelChosen").asText()).isNotBlank();

        String metrics = mlAnswer.path("metricsDescribed").asText("").toLowerCase();
        assertThat(metrics).containsAnyOf("f1", "auc", "precision", "recall");
        assertThat(mlAnswer.path("deploymentStrategy").asText()).isNotBlank();
    }

    @Test
    void generateFollowUp_returnsNonEmptyString() throws Exception {
        FlowContext flow = createAnswerFlow("SE");
        triggerExtraction(flow.answerId);

        JsonNode mlAnswer = pollMlAnswer(flow.answerId, 10, 2000);
        assertThat(mlAnswer.path("aiEvaluationScore").isNumber()).isTrue();

        String followUp = mlScenarioAnswerService.generateFollowUp(flow.answerId);
        assertThat(followUp).isNotBlank();

        String modelChosen = mlAnswer.path("modelChosen").asText("");
        assertThat(followUp.toLowerCase()).containsAnyOf(
                modelChosen.toLowerCase(),
                "what metrics would you use",
                "how would you deploy",
                "how would you preprocess"
        );
    }

    @Test
    void submitDiagramOrder_storedInAnswer() throws Exception {
        JsonNode started = startAiPracticeTechnicalSession();
        long sessionId = started.path("id").asLong();

        JsonNode firstQuestion = getCurrentQuestion(sessionId);
        long firstQuestionId = firstQuestion.path("id").asLong();

        submitAnswer(sessionId, firstQuestionId,
                "I would start by ingesting data then preprocess and engineer features.",
                null);

        JsonNode secondQuestion = getCurrentQuestion(sessionId);
        long secondQuestionId = secondQuestion.path("id").asLong();

        String pipelineOrder = "PIPELINE:[\"ingestion\",\"preprocessing\",\"features\",\"training\",\"evaluation\",\"deployment\",\"monitoring\"]";
        JsonNode secondAnswer = submitAnswer(
                sessionId,
                secondQuestionId,
                "I would start with data ingestion then preprocessing then feature engineering.",
                pipelineOrder
        );

        long secondAnswerId = secondAnswer.path("id").asLong();

        String loadedBody = mockMvc.perform(get("/api/v1/answers/{id}", secondAnswerId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode loaded = objectMapper.readTree(loadedBody);
        assertThat(loaded.path("codeAnswer").asText()).startsWith("PIPELINE:");
    }

    @Test
    void abandonSession_marksAbandoned() throws Exception {
        JsonNode started = startAiPracticeTechnicalSession();
        long sessionId = started.path("id").asLong();

        String body = mockMvc.perform(put("/api/v1/sessions/{id}/abandon", sessionId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode abandoned = objectMapper.readTree(body);
        assertThat(abandoned.path("status").asText()).isEqualTo("ABANDONED");
    }

    private FlowContext createAnswerFlow(String role) throws Exception {
        JsonNode started = startPracticeTechnicalSession(role);
        long sessionId = started.path("id").asLong();

        JsonNode question = getCurrentQuestion(sessionId);
        long questionId = question.path("id").asLong();

        JsonNode answer = submitAnswer(
                sessionId,
                questionId,
                "I would use XGBoost for this classification task. I would encode categorical features and normalize numerical ones. I would evaluate using F1 score and AUC-ROC. For deployment I would wrap it in a FastAPI service.",
                null
        );

        return new FlowContext(sessionId, questionId, answer.path("id").asLong());
    }

    private JsonNode startAiPracticeTechnicalSession() throws Exception {
        return startPracticeTechnicalSession("AI");
    }

    private JsonNode startPracticeTechnicalSession(String role) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "userId", 1,
                "careerPathId", 1,
                "role", role,
                "mode", "PRACTICE",
                "type", "TECHNICAL",
                "questionCount", 5
        ));

        String body = mockMvc.perform(post("/api/v1/sessions/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }

    private JsonNode getCurrentQuestion(long sessionId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/sessions/{sessionId}/questions/current", sessionId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }

    private JsonNode submitAnswer(long sessionId, long questionId, String answerText, String codeAnswer) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("sessionId", sessionId);
        request.put("questionId", questionId);
        request.put("answerText", answerText);
        request.put("videoUrl", null);
        request.put("audioUrl", null);
        if (codeAnswer != null) {
            request.put("codeAnswer", codeAnswer);
        }

        String body = mockMvc.perform(post("/api/v1/answers/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }

    private void triggerExtraction(long answerId) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "transcript",
                "I would use XGBoost, encoding, normalization, F1, AUC and deploy through FastAPI."
        ));

        mockMvc.perform(post("/api/v1/ml-answers/extract/{answerId}", answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is2xxSuccessful());
    }

    private JsonNode pollMlAnswer(long answerId, int maxAttempts, long delayMs) throws Exception {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                var response = mockMvc.perform(get("/api/v1/ml-answers/answer/{answerId}", answerId))
                        .andReturn()
                        .getResponse();

                if (response.getStatus() == 200) {
                    JsonNode node = objectMapper.readTree(response.getContentAsString());
                    if (node.path("modelChosen").asText("").isBlank()) {
                        Thread.sleep(delayMs);
                        continue;
                    }

                    if (!node.path("aiEvaluationScore").isNumber()) {
                        Thread.sleep(delayMs);
                        continue;
                    }

                    return node;
                }
            } catch (Exception ignored) {
                // Extraction can be in-flight and the row may not exist yet.
            }

            Thread.sleep(delayMs);
        }

        throw new AssertionError("ML answer did not contain extracted concepts within polling window");
    }

    private record FlowContext(long sessionId, long questionId, long answerId) {
    }
}
