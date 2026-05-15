package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.response.AuditLogResponse;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditLogServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private AuditLogService auditLogService;

    @Test
    void logOperation_stateCompletedOperation_expectedPersistedAndRetrievableByUser() {
        UUID entityId = UUID.randomUUID();

        auditLogService.logOperation(
                TestConstants.USER_ID,
                OperationType.CV_PARSE,
                "CandidateCV",
                entityId,
                ProcessingStatus.COMPLETED,
                1200,
                180,
                null
        );

        List<AuditLogResponse> logs = auditLogService.getLogsForUser(TestConstants.USER_ID);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).operationType()).isEqualTo(OperationType.CV_PARSE);
        assertThat(logs.get(0).entityId()).isEqualTo(entityId);
        assertThat(logs.get(0).status()).isEqualTo(ProcessingStatus.COMPLETED);
    }

    @Test
    void getLogsByOperation_stateMultipleOperationTypes_expectedOnlyRequestedTypeReturned() {
        auditLogService.logOperation(
                TestConstants.USER_ID,
                OperationType.CV_PARSE,
                "CandidateCV",
                UUID.randomUUID(),
                ProcessingStatus.COMPLETED,
                100,
                30,
                null
        );
        auditLogService.logOperation(
                TestConstants.USER_ID,
                OperationType.GITHUB_AUDIT,
                "GitHubProfile",
                UUID.randomUUID(),
                ProcessingStatus.COMPLETED,
                900,
                110,
                null
        );

        List<AuditLogResponse> githubLogs = auditLogService.getLogsByOperation(TestConstants.USER_ID, OperationType.GITHUB_AUDIT);

        assertThat(githubLogs).hasSize(1);
        assertThat(githubLogs.get(0).operationType()).isEqualTo(OperationType.GITHUB_AUDIT);
    }

    @Test
    void getLogsForEntity_stateSharedEntityIdAcrossEvents_expectedEntityScopedResultsReturned() {
        UUID entityId = UUID.randomUUID();

        auditLogService.logOperation(
                TestConstants.USER_ID,
                OperationType.LINKEDIN_SCRAPE,
                "LinkedInProfile",
                entityId,
                ProcessingStatus.COMPLETED,
                350,
                60,
                null
        );
        auditLogService.logOperation(
                TestConstants.ALT_USER_ID,
                OperationType.LINKEDIN_ANALYZE,
                "LinkedInProfile",
                entityId,
                ProcessingStatus.COMPLETED,
                450,
                80,
                null
        );

        List<AuditLogResponse> entityLogs = auditLogService.getLogsForEntity("LinkedInProfile", entityId);

        assertThat(entityLogs).hasSize(2);
        assertThat(entityLogs)
                .extracting(AuditLogResponse::operationType)
                .containsExactlyInAnyOrder(OperationType.LINKEDIN_SCRAPE, OperationType.LINKEDIN_ANALYZE);
    }
}
