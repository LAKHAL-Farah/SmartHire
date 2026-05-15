package tn.esprit.msprofile.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullCvWorkflowE2ETest extends AbstractE2ETest {

    static String uploadedCvId;
    static String jobOfferId;
    static String versionId;

    @Test
    @Order(1)
    void uploadCv_withValidPdf_returnsCreatedAndParsesContent() throws Exception {
        stubNvidiaParseSuccess();
        stubNvidiaScoreSuccess();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/cv/upload")
                        .file(TestFixtures.minimalPdfCv()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        uploadedCvId = (String) body.get("id");

        assertThat(uploadedCvId).isNotBlank();
        assertThat(body.get("originalFileName")).isEqualTo("test-cv.pdf");

        await().atMost(5, SECONDS).untilAsserted(() -> {
            MvcResult poll = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/{cvId}", uploadedCvId)).andReturn();
            Map<?, ?> polledBody = objectMapper.readValue(poll.getResponse().getContentAsString(), Map.class);
            assertThat(polledBody.get("parseStatus")).isEqualTo("COMPLETED");
            assertThat((String) polledBody.get("parsedContent")).contains("Alice Martin");
            assertThat(((Number) polledBody.get("atsScore")).intValue()).isGreaterThanOrEqualTo(0);
        });

        wireMock.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/chat/completions")));
    }

    @Test
    @Order(2)
    void getCv_afterUpload_returnsCvWithScore() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/{cvId}", uploadedCvId)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("id")).isEqualTo(uploadedCvId);
        assertThat(body.get("atsScore")).isNotNull();
        assertThat(body.get("parseStatus")).isEqualTo("COMPLETED");
    }

    @Test
    @Order(3)
    void getCvScore_afterUpload_returnsValidScore() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/{cvId}/score", uploadedCvId)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        int score = ((Number) body.get("atsScore")).intValue();

        assertThat(body.get("cvId")).isEqualTo(uploadedCvId);
        assertThat(score).isBetween(0, 100);
    }

    @Test
    @Order(4)
    void listCvs_afterUpload_returnsNonEmptyList() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        var nodes = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(nodes.isArray()).isTrue();
        assertThat(nodes.size()).isGreaterThanOrEqualTo(1);
        assertThat(nodes.get(0).get("id").asText()).isEqualTo(uploadedCvId);
    }

    @Test
    @Order(5)
    void createJobOffer_withValidRequest_returnsCreatedAndExtractsKeywords() throws Exception {
        stubNvidiaKeywordsSuccess();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/job-offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestFixtures.createJobOfferRequestJson()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        jobOfferId = (String) body.get("id");

        assertThat(jobOfferId).isNotBlank();
        assertThat(body.get("title")).isEqualTo("Senior Full-Stack Engineer");
        String extracted = (String) body.get("extractedKeywords");
        assertThat(extracted).isNotBlank();
        assertThat(extracted).contains("Java");
        assertThat(extracted).contains("Spring Boot");

        wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/chat/completions")));
    }

    @Test
    @Order(6)
    void listJobOffers_afterCreation_returnsOfferInList() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/job-offers")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        var nodes = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(nodes.isArray()).isTrue();
        assertThat(nodes.size()).isGreaterThanOrEqualTo(1);

        boolean found = false;
        for (int i = 0; i < nodes.size(); i++) {
            if (jobOfferId.equals(nodes.get(i).get("id").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @Order(7)
    void tailorCv_withValidCvAndJobOffer_returnsTailoredVersion() throws Exception {
        stubNvidiaTailorSuccess();
        stubNvidiaScoreSuccess();
        String payload = objectMapper.writeValueAsString(Map.of("jobOfferId", jobOfferId));

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/cv/{cvId}/tailor", uploadedCvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        versionId = (String) body.get("id");

        assertThat(versionId).isNotBlank();
        assertThat(body.get("versionType")).isEqualTo("TAILORED");
        assertThat(((Number) body.get("atsScore")).intValue()).isGreaterThanOrEqualTo(0);

        Object keywordMatchRate = body.get("keywordMatchRate");
        assertThat(keywordMatchRate).isNotNull();
        double ratio = Double.parseDouble(keywordMatchRate.toString());
        assertThat(ratio).isBetween(0.0, 1.0);

        assertThat((String) body.get("tailoredContent")).contains("Microservices");

        wireMock.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/chat/completions")));
    }

    @Test
    @Order(8)
    void listVersions_afterTailor_returnsTailoredVersion() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/{cvId}/versions", uploadedCvId)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        var nodes = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(nodes.isArray()).isTrue();
        assertThat(nodes.size()).isGreaterThanOrEqualTo(1);

        boolean foundVersionId = false;
        boolean foundTailored = false;
        for (int i = 0; i < nodes.size(); i++) {
            if (versionId.equals(nodes.get(i).get("id").asText())) {
                foundVersionId = true;
            }
            if ("TAILORED".equals(nodes.get(i).get("versionType").asText())) {
                foundTailored = true;
            }
        }

        assertThat(foundVersionId).isTrue();
        assertThat(foundTailored).isTrue();
    }

    @Test
    @Order(9)
    void getVersion_byId_returnsCorrectVersion() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/versions/{versionId}", versionId)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("id")).isEqualTo(versionId);
        assertThat(body.get("versionType")).isEqualTo("TAILORED");
    }

    @Test
    @Order(10)
    void exportVersionAsPdf_returnsValidPdfBytes() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/versions/{versionId}/export", versionId)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(4);

        String magic = new String(bytes, 0, 4, StandardCharsets.UTF_8);
        assertThat(magic).isEqualTo("%PDF");
    }

    @Test
    @Order(11)
    void deleteJobOffer_returnsNoContent() throws Exception {
        MvcResult delete = mockMvc.perform(MockMvcRequestBuilders.delete("/api/job-offers/{jobOfferId}", jobOfferId)).andReturn();
        assertThat(delete.getResponse().getStatus()).isEqualTo(204);

        MvcResult get = mockMvc.perform(MockMvcRequestBuilders.get("/api/job-offers/{jobOfferId}", jobOfferId)).andReturn();
        assertThat(get.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @Order(12)
    void deleteCv_returnsNoContent() throws Exception {
        MvcResult delete = mockMvc.perform(MockMvcRequestBuilders.delete("/api/cv/{cvId}", uploadedCvId)).andReturn();
        assertThat(delete.getResponse().getStatus()).isEqualTo(204);

        MvcResult get = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/{cvId}", uploadedCvId)).andReturn();
        int status = get.getResponse().getStatus();
        assertThat(status == 404 || status == 200).isTrue();

        if (status == 200) {
            Map<?, ?> body = objectMapper.readValue(get.getResponse().getContentAsString(), Map.class);
            assertThat(body.get("isActive")).isEqualTo(false);
        }
    }
}
