package tn.esprit.msprofile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.request.JobOfferRequest;
import tn.esprit.msprofile.dto.response.JobOfferResponse;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.JobOfferService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = tn.esprit.msprofile.controller.m4.JobOfferController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobOfferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobOfferService jobOfferService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void createJobOffer_stateValidPayload_expectedCreated() throws Exception {
        JobOfferResponse response = sampleResponse();
        when(jobOfferService.createJobOffer(eq(USER_ID), any(JobOfferRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/job-offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Senior Java Engineer",
                                  "company":"SmartHire",
                                  "rawDescription":"Need Java Spring Docker testing skills",
                                  "sourceUrl":"https://example.com/jobs/1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.title").value("Senior Java Engineer"));
    }

    @Test
    void createJobOffer_stateInvalidPayload_expectedBadRequest() throws Exception {
        mockMvc.perform(post("/api/job-offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"",
                                  "rawDescription":""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request payload"));
    }

    @Test
    void getJobOffersForUser_stateOffersExist_expectedOk() throws Exception {
        when(jobOfferService.getJobOffersForUser(eq(USER_ID))).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/job-offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()));
    }

    @Test
    void getJobOffersForUser_stateServiceThrowsException_expectedInternalServerError() throws Exception {
        when(jobOfferService.getJobOffersForUser(eq(USER_ID))).thenThrow(new IllegalStateException("offer list failed"));

        mockMvc.perform(get("/api/job-offers"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("offer list failed"));
    }

    @Test
    void getJobOfferById_stateOfferExists_expectedOk() throws Exception {
        JobOfferResponse response = sampleResponse();
        when(jobOfferService.getJobOfferById(eq(response.id()), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/job-offers/{jobOfferId}", response.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()));
    }

    @Test
    void getJobOfferById_stateOfferMissing_expectedNotFound() throws Exception {
        UUID jobOfferId = UUID.randomUUID();
        when(jobOfferService.getJobOfferById(eq(jobOfferId), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("job offer not found"));

        mockMvc.perform(get("/api/job-offers/{jobOfferId}", jobOfferId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("job offer not found"));
    }

    @Test
    void deleteJobOffer_stateOfferExists_expectedNoContent() throws Exception {
        UUID jobOfferId = UUID.randomUUID();

        mockMvc.perform(delete("/api/job-offers/{jobOfferId}", jobOfferId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteJobOffer_stateOfferMissing_expectedNotFound() throws Exception {
        UUID jobOfferId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("delete target missing"))
                .when(jobOfferService)
                .deleteJobOffer(eq(jobOfferId), eq(USER_ID));

        mockMvc.perform(delete("/api/job-offers/{jobOfferId}", jobOfferId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("delete target missing"));
    }

    private JobOfferResponse sampleResponse() {
        return new JobOfferResponse(
                UUID.randomUUID(),
                USER_ID,
                "Senior Java Engineer",
                "SmartHire",
                "Need Java Spring Docker testing skills",
                "[\"java\",\"spring\"]",
                "https://example.com/jobs/1",
                Instant.now()
        );
    }
}
