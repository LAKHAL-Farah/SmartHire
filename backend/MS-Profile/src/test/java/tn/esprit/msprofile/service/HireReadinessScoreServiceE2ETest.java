package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.response.HireReadinessScoreResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.AuditLogRepository;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.HireReadinessScoreRepository;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class HireReadinessScoreServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private HireReadinessScoreService hireReadinessScoreService;

    @Autowired
    private CandidateCVRepository candidateCVRepository;

    @Autowired
    private LinkedInProfileRepository linkedInProfileRepository;

    @Autowired
    private GitHubProfileRepository gitHubProfileRepository;

    @Autowired
    private HireReadinessScoreRepository hireReadinessScoreRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void computeAndSaveScore_stateAllInputsPresent_expectedWeightedScorePersistedAndLogged() {
        persistCv(80, true);
        persistLinkedIn(70);
        persistGitHub(90, "score-user");

        HireReadinessScoreResponse response = hireReadinessScoreService.computeAndSaveScore(TestConstants.USER_ID);

        assertThat(response.cvScore()).isEqualTo(80);
        assertThat(response.linkedinScore()).isEqualTo(70);
        assertThat(response.githubScore()).isEqualTo(90);
        assertThat(response.globalScore()).isEqualTo(80);

        assertThat(hireReadinessScoreRepository.findByUserId(TestConstants.USER_ID)).isPresent();

        List<AuditLog> logs = auditLogRepository.findByUserIdAndOperationType(TestConstants.USER_ID, OperationType.SCORE_COMPUTE);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(logs.get(0).getTokensUsed()).isGreaterThan(0);
    }

    @Test
    void refreshScore_stateDataChanged_expectedUpdatedGlobalScore() {
        persistCv(50, true);
        HireReadinessScoreResponse first = hireReadinessScoreService.computeAndSaveScore(TestConstants.USER_ID);
        assertThat(first.globalScore()).isEqualTo(20);

        persistLinkedIn(80);
        persistGitHub(90, "refresh-user");

        HireReadinessScoreResponse refreshed = hireReadinessScoreService.refreshScore(TestConstants.USER_ID);

        assertThat(refreshed.globalScore()).isEqualTo(71);
    }

    @Test
    void getScoreForUser_stateMissingScore_expectedResourceNotFoundException() {
        assertThatThrownBy(() -> hireReadinessScoreService.getScoreForUser(TestConstants.USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("HireReadinessScore not found");
    }

    private void persistCv(int atsScore, boolean active) {
        CandidateCV cv = new CandidateCV();
        cv.setUserId(TestConstants.USER_ID);
        cv.setOriginalFileUrl("temp/cv-uploads/score.pdf");
        cv.setOriginalFileName("score.pdf");
        cv.setFileFormat(FileFormat.PDF);
        cv.setParsedContent("{\"rawText\":\"java spring\"}");
        cv.setParseStatus(ProcessingStatus.COMPLETED);
        cv.setAtsScore(atsScore);
        cv.setIsActive(active);
        cv.setUploadedAt(Instant.now());
        cv.setUpdatedAt(Instant.now());
        candidateCVRepository.save(cv);
    }

    private void persistLinkedIn(int globalScore) {
        LinkedInProfile profile = new LinkedInProfile();
        profile.setUserId(TestConstants.USER_ID);
        profile.setProfileUrl("https://linkedin.com/in/test-user");
        profile.setRawContent("Java Spring Docker profile");
        profile.setScrapeStatus(ProcessingStatus.COMPLETED);
        profile.setGlobalScore(globalScore);
        profile.setSectionScoresJson("{\"headline\":80}");
        profile.setCreatedAt(Instant.now());
        profile.setAnalyzedAt(Instant.now());
        linkedInProfileRepository.save(profile);
    }

    private void persistGitHub(int overallScore, String username) {
        GitHubProfile profile = new GitHubProfile();
        profile.setUserId(TestConstants.USER_ID);
        profile.setGithubUsername(username);
        profile.setOverallScore(overallScore);
        profile.setRepoCount(3);
        profile.setTopLanguages("[\"Java\",\"TypeScript\"]");
        profile.setProfileReadmeScore(75);
        profile.setAuditStatus(ProcessingStatus.COMPLETED);
        profile.setCreatedAt(Instant.now());
        profile.setAnalyzedAt(Instant.now());
        gitHubProfileRepository.save(profile);
    }
}
