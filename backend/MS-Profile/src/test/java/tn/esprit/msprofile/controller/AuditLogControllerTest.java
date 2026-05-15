package tn.esprit.msprofile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.AuditLogResponse;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.service.AuditLogService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = AuditLogWorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void getAuditLogs_stateWithoutFilter_expectedUserLogs() throws Exception {
        AuditLogResponse response = new AuditLogResponse(
                UUID.randomUUID(),
                USER_ID,
                OperationType.CV_PARSE,
                "CandidateCV",
                UUID.randomUUID(),
                1200,
                150,
                ProcessingStatus.COMPLETED,
                null,
                "parsed sample cv",
                Instant.now()
        );

        when(auditLogService.getLogsForUser(eq(USER_ID))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationType").value("CV_PARSE"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void getAuditLogs_stateWithOperationTypeFilter_expectedFilteredLogs() throws Exception {
        AuditLogResponse response = new AuditLogResponse(
                UUID.randomUUID(),
                USER_ID,
                OperationType.GITHUB_AUDIT,
                "GitHubProfile",
                UUID.randomUUID(),
                1600,
                180,
                ProcessingStatus.COMPLETED,
                null,
                "audited repositories",
                Instant.now()
        );

        when(auditLogService.getLogsByOperation(eq(USER_ID), eq(OperationType.GITHUB_AUDIT))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/audit-logs").param("operationType", "GITHUB_AUDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operationType").value("GITHUB_AUDIT"));
    }

    @Test
    void getAuditLogs_stateServiceThrowsException_expectedInternalServerError() throws Exception {
        when(auditLogService.getLogsByOperation(eq(USER_ID), eq(OperationType.CV_SCORE)))
                .thenThrow(new IllegalStateException("unexpected audit failure"));

        mockMvc.perform(get("/api/audit-logs").param("operationType", "CV_SCORE"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("unexpected audit failure"));
    }
}
