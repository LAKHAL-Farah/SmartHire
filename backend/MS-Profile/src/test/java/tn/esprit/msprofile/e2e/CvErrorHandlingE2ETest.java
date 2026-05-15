package tn.esprit.msprofile.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CvErrorHandlingE2ETest extends AbstractE2ETest {

    @Test
    void uploadCv_withNoFile_returns400() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/cv/upload")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void uploadCv_withTextFile_returns400() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "resume.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "plain text".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/cv/upload").file(textFile)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        String body = result.getResponse().getContentAsString();
        assertThat(body.toLowerCase()).contains("pdf");
    }

    @Test
    void uploadCv_whenOpenAiFails_returns502OrMarksAsFailed() throws Exception {
        wireMock.stubFor(post(urlPathMatching(".*/chat/completions"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody("{\"error\":\"boom\"}")));

        MvcResult upload = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/cv/upload")
                        .file(TestFixtures.minimalPdfCv()))
                .andReturn();

        int status = upload.getResponse().getStatus();
        assertThat(status == 502 || status == 201).isTrue();

        if (status == 502) {
            assertThat(upload.getResponse().getContentAsString().toLowerCase()).contains("ai service");
        } else {
            Map<?, ?> body = objectMapper.readValue(upload.getResponse().getContentAsString(), Map.class);
            assertThat(body.get("parseStatus")).isEqualTo("FAILED");
        }
    }

    @Test
    void getCv_withNonExistentId_returns404() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/cv/{cvId}", UUID.randomUUID())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString().toLowerCase()).contains("not found");
    }

    @Test
    void tailorCv_withNonExistentJobOffer_returns404() throws Exception {
        stubNvidiaParseSuccess();
        stubNvidiaScoreSuccess();
        MvcResult upload = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/cv/upload")
                        .file(TestFixtures.minimalPdfCv()))
                .andReturn();

        Map<?, ?> uploaded = objectMapper.readValue(upload.getResponse().getContentAsString(), Map.class);
        String cvId = (String) uploaded.get("id");

        String payload = objectMapper.writeValueAsString(Map.of("jobOfferId", UUID.randomUUID().toString()));
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/cv/{cvId}/tailor", cvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void tailorCv_withNonExistentCv_returns404() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("jobOfferId", UUID.randomUUID().toString()));

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/cv/{cvId}/tailor", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void tailorCv_withMissingJobOfferId_returns400() throws Exception {
        stubNvidiaParseSuccess();
        stubNvidiaScoreSuccess();
        MvcResult upload = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/cv/upload")
                        .file(TestFixtures.minimalPdfCv()))
                .andReturn();

        Map<?, ?> uploaded = objectMapper.readValue(upload.getResponse().getContentAsString(), Map.class);
        String cvId = (String) uploaded.get("id");

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/cv/{cvId}/tailor", cvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }
}
