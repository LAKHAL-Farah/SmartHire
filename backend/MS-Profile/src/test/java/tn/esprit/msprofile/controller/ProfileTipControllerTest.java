package tn.esprit.msprofile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.entity.enums.TipPriority;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.ProfileTipService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = ProfileTipWorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileTipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileTipService profileTipService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void getTips_stateWithoutTypeFilter_expectedUnresolvedTips() throws Exception {
        when(profileTipService.getTipsForUser(eq(USER_ID))).thenReturn(List.of(sampleResponse(ProfileType.CV)));

        mockMvc.perform(get("/api/tips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileType").value("CV"));
    }

    @Test
    void getTips_stateWithTypeFilter_expectedFilteredTips() throws Exception {
        when(profileTipService.getTipsByType(eq(USER_ID), eq(ProfileType.GITHUB))).thenReturn(List.of(sampleResponse(ProfileType.GITHUB)));

        mockMvc.perform(get("/api/tips").param("type", "GITHUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profileType").value("GITHUB"));
    }

    @Test
    void getTips_stateInvalidTypeFilter_expectedBadRequest() throws Exception {
        mockMvc.perform(get("/api/tips").param("type", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolveTip_stateTipExists_expectedNoContent() throws Exception {
        UUID tipId = UUID.randomUUID();

        mockMvc.perform(patch("/api/tips/{tipId}/resolve", tipId))
                .andExpect(status().isNoContent());
    }

    @Test
    void resolveTip_stateTipMissing_expectedNotFound() throws Exception {
        UUID tipId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("tip missing"))
                .when(profileTipService)
                .markTipAsResolved(eq(tipId), eq(USER_ID));

        mockMvc.perform(patch("/api/tips/{tipId}/resolve", tipId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("tip missing"));
    }

    private ProfileTipResponse sampleResponse(ProfileType type) {
        return new ProfileTipResponse(
                UUID.randomUUID(),
                USER_ID,
                type,
                UUID.randomUUID(),
                "Improve your keyword density and measurable impact bullets.",
                TipPriority.HIGH,
                false,
                Instant.now()
        );
    }
}
