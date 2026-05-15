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
import tn.esprit.msprofile.dto.response.LinkedInProfileResponse;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.LinkedInProfileService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = LinkedInController.class)
@AutoConfigureMockMvc(addFilters = false)
class LinkedInProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LinkedInProfileService linkedInProfileService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void analyze_stateValidPayload_expectedOk() throws Exception {
        LinkedInProfileResponse response = sampleResponse();
        when(linkedInProfileService.analyzeLinkedInProfile(eq(USER_ID), eq("https://www.linkedin.com/in/jane-doe")))
                .thenReturn(response);

        mockMvc.perform(post("/api/linkedin/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileUrl\":\"https://www.linkedin.com/in/jane-doe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.scrapeStatus").value("COMPLETED"));
    }

    @Test
    void analyze_stateInvalidPayload_expectedBadRequest() throws Exception {
        mockMvc.perform(post("/api/linkedin/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileUrl\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request payload"));
    }

    @Test
    void getLinkedInProfile_stateProfileExists_expectedOk() throws Exception {
        LinkedInProfileResponse response = sampleResponse();
        when(linkedInProfileService.getLinkedInProfileForUser(eq(USER_ID))).thenReturn(response);

        mockMvc.perform(get("/api/linkedin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileUrl").value("https://www.linkedin.com/in/jane-doe"));
    }

    @Test
    void getLinkedInProfile_stateProfileMissing_expectedNotFound() throws Exception {
        when(linkedInProfileService.getLinkedInProfileForUser(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("linkedin profile missing"));

        mockMvc.perform(get("/api/linkedin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("linkedin profile missing"));
    }

    @Test
    void reanalyze_stateProfileExists_expectedOk() throws Exception {
        LinkedInProfileResponse response = sampleResponse();
        when(linkedInProfileService.reanalyzeProfile(eq(USER_ID))).thenReturn(response);

        mockMvc.perform(post("/api/linkedin/reanalyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalScore").value(79));
    }

    @Test
    void reanalyze_stateProfileMissing_expectedNotFound() throws Exception {
        when(linkedInProfileService.reanalyzeProfile(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("reanalyze target missing"));

        mockMvc.perform(post("/api/linkedin/reanalyze"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("reanalyze target missing"));
    }

    private LinkedInProfileResponse sampleResponse() {
        return new LinkedInProfileResponse(
                UUID.randomUUID(),
                USER_ID,
                "https://www.linkedin.com/in/jane-doe",
                "raw linkedIn content",
                ProcessingStatus.COMPLETED,
                null,
                79,
                "{\"headline\":82,\"summary\":75}",
                Instant.now(),
                "Senior Java Engineer",
                "Senior Java Engineer | SPRING | DOCKER",
                "Current summary",
                "Optimized summary",
                "JAVA, SPRING, DOCKER",
                Instant.now()
        );
    }
}
