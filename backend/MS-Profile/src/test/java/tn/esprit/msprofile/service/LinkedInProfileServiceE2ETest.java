package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.response.LinkedInProfileResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.repository.AuditLogRepository;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;
import tn.esprit.msprofile.testsupport.TestFixtureLoader;

import java.time.Instant;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class LinkedInProfileServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private LinkedInProfileService linkedInProfileService;

    @Autowired
    private LinkedInProfileRepository linkedInProfileRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void analyzeLinkedInProfile_stateReachableUrl_expectedCompletedAnalysisAndAuditLogs() {
        String html = TestFixtureLoader.readText("fixtures/linkedin-profile.html");
        stubLinkedInProfilePage(TestConstants.LINKEDIN_PROFILE_PATH, html);

        String profileUrl = "http://localhost:" + WIREMOCK.port() + TestConstants.LINKEDIN_PROFILE_PATH;
        LinkedInProfileResponse response = linkedInProfileService.analyzeLinkedInProfile(TestConstants.USER_ID, profileUrl);

        assertThat(response.scrapeStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(response.globalScore()).isNotNull();

        await().atMost(10, SECONDS).untilAsserted(() -> {
            LinkedInProfile profile = linkedInProfileRepository.findByUserId(TestConstants.USER_ID).orElseThrow();
            assertThat(profile.getOptimizedHeadline()).isNotBlank();
            assertThat(profile.getOptimizedSummary()).isNotBlank();
            assertThat(profile.getOptimizedSkills()).isNotBlank();
        });

        List<AuditLog> scrapeLogs = auditLogRepository.findByUserIdAndOperationType(TestConstants.USER_ID, OperationType.LINKEDIN_SCRAPE);
        List<AuditLog> analyzeLogs = auditLogRepository.findByUserIdAndOperationType(TestConstants.USER_ID, OperationType.LINKEDIN_ANALYZE);

        assertThat(scrapeLogs).isNotEmpty();
        assertThat(analyzeLogs).isNotEmpty();
        assertThat(scrapeLogs.get(0).getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(analyzeLogs.get(0).getTokensUsed()).isGreaterThan(0);
    }

    @Test
    void reanalyzeProfile_stateExistingProfile_expectedTimestampUpdated() {
        String html = TestFixtureLoader.readText("fixtures/linkedin-profile.html");
        stubLinkedInProfilePage(TestConstants.LINKEDIN_PROFILE_PATH, html);

        String profileUrl = "http://localhost:" + WIREMOCK.port() + TestConstants.LINKEDIN_PROFILE_PATH;
        LinkedInProfileResponse first = linkedInProfileService.analyzeLinkedInProfile(TestConstants.USER_ID, profileUrl);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            LinkedInProfile profile = linkedInProfileRepository.findByUserId(TestConstants.USER_ID).orElseThrow();
            assertThat(profile.getAnalyzedAt()).isNotNull();
        });

        Instant initialAnalyzedAt = linkedInProfileRepository.findByUserId(TestConstants.USER_ID).orElseThrow().getAnalyzedAt();

        LinkedInProfileResponse second = linkedInProfileService.reanalyzeProfile(TestConstants.USER_ID);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.analyzedAt()).isAfterOrEqualTo(initialAnalyzedAt);
    }

    @Test
    void analyzeLinkedInProfile_stateUnreachableUrl_expectedFailedStatusAndErrorMessage() {
        String unreachableUrl = "http://localhost:" + WIREMOCK.port() + "/linkedin/profile/not-found";

        LinkedInProfileResponse response = linkedInProfileService.analyzeLinkedInProfile(TestConstants.USER_ID, unreachableUrl);

        assertThat(response.scrapeStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(response.scrapeErrorMessage()).isNotBlank();

        List<AuditLog> logs = auditLogRepository.findByUserIdAndOperationType(TestConstants.USER_ID, OperationType.LINKEDIN_SCRAPE);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(ProcessingStatus.FAILED);
    }
}
