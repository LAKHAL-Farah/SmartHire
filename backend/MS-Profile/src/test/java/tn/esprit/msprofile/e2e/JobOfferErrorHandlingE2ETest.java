package tn.esprit.msprofile.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class JobOfferErrorHandlingE2ETest extends AbstractE2ETest {

    @Test
    void createJobOffer_withMissingTitle_returns400() throws Exception {
        String payload = """
                {
                  "rawDescription": "Need Java and Spring"
                }
                """;

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/job-offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void createJobOffer_withMissingDescription_returns400() throws Exception {
        String payload = """
                {
                  "title": "Engineer"
                }
                """;

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/job-offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void getJobOffer_withNonExistentId_returns404() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/job-offers/{jobOfferId}", UUID.randomUUID())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void deleteJobOffer_withNonExistentId_returns404() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/job-offers/{jobOfferId}", UUID.randomUUID())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
