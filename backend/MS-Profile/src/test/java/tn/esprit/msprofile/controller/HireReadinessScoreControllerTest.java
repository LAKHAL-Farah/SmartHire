package tn.esprit.msprofile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.HireReadinessScoreResponse;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.HireReadinessScoreService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = ScoreWorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
class HireReadinessScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HireReadinessScoreService hireReadinessScoreService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void getScore_stateScoreExists_expectedOk() throws Exception {
        when(hireReadinessScoreService.getScoreForUser(eq(USER_ID))).thenReturn(sampleResponse(74));

        mockMvc.perform(get("/api/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalScore").value(74));
    }

    @Test
    void getScore_stateScoreMissing_expectedFallbackComputeAndOk() throws Exception {
        when(hireReadinessScoreService.getScoreForUser(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("score missing"));
        when(hireReadinessScoreService.computeAndSaveScore(eq(USER_ID))).thenReturn(sampleResponse(69));

        mockMvc.perform(get("/api/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalScore").value(69));
    }

    @Test
    void getScore_stateFallbackComputeFails_expectedInternalServerError() throws Exception {
        when(hireReadinessScoreService.getScoreForUser(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("score missing"));
        when(hireReadinessScoreService.computeAndSaveScore(eq(USER_ID)))
                .thenThrow(new IllegalStateException("compute failed"));

        mockMvc.perform(get("/api/score"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("compute failed"));
    }

    @Test
    void refreshScore_stateServiceSucceeds_expectedOk() throws Exception {
        when(hireReadinessScoreService.refreshScore(eq(USER_ID))).thenReturn(sampleResponse(81));

        mockMvc.perform(post("/api/score/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalScore").value(81));
    }

    @Test
    void refreshScore_stateServiceFails_expectedInternalServerError() throws Exception {
        when(hireReadinessScoreService.refreshScore(eq(USER_ID)))
                .thenThrow(new IllegalStateException("refresh failed"));

        mockMvc.perform(post("/api/score/refresh"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("refresh failed"));
    }

    private HireReadinessScoreResponse sampleResponse(int globalScore) {
        return new HireReadinessScoreResponse(
                UUID.randomUUID(),
                USER_ID,
                75,
                71,
                76,
                globalScore,
                Instant.now()
        );
    }
}
