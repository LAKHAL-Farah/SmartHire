package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.GitHubRepository;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.ProfileTip;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.entity.enums.TipPriority;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.GitHubRepositoryRepository;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;
import tn.esprit.msprofile.repository.ProfileTipRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProfileTipServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private ProfileTipService profileTipService;

    @Autowired
    private CandidateCVRepository candidateCVRepository;

    @Autowired
    private LinkedInProfileRepository linkedInProfileRepository;

    @Autowired
    private GitHubProfileRepository gitHubProfileRepository;

    @Autowired
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    @Autowired
    private ProfileTipRepository profileTipRepository;

    @Test
    void generateTipsForCv_stateLowAtsScore_expectedHighPriorityTipsCreated() {
        CandidateCV cv = persistCv(55, ProcessingStatus.COMPLETED, "");

        List<ProfileTip> tips = profileTipService.generateTipsForCv(TestConstants.USER_ID, cv.getId());

        assertThat(tips).isNotEmpty();
        assertThat(tips)
                .extracting(ProfileTip::getPriority)
                .contains(TipPriority.HIGH);
        assertThat(tips)
                .extracting(ProfileTip::getProfileType)
                .containsOnly(ProfileType.CV);
    }

    @Test
    void generateTipsForLinkedIn_stateMissingOptimizedFields_expectedActionableTipsCreated() {
        persistLinkedIn(58, null, null);

        List<ProfileTip> tips = profileTipService.generateTipsForLinkedIn(TestConstants.USER_ID);

        assertThat(tips).isNotEmpty();
        assertThat(tips)
                .extracting(ProfileTip::getProfileType)
                .containsOnly(ProfileType.LINKEDIN);
        assertThat(tips)
                .extracting(ProfileTip::getTipText)
                .anyMatch(text -> text.contains("headline"));
    }

    @Test
    void generateTipsForGitHub_stateReposMissingSignals_expectedGitHubTipsCreated() {
        GitHubProfile profile = persistGitHubProfile(62, "tips-user");
        persistGitHubRepository(profile, "repo-one", false, false, 50);
        persistGitHubRepository(profile, "repo-two", false, true, 55);

        List<ProfileTip> tips = profileTipService.generateTipsForGitHub(TestConstants.USER_ID);

        assertThat(tips).isNotEmpty();
        assertThat(tips)
                .extracting(ProfileTip::getProfileType)
                .containsOnly(ProfileType.GITHUB);
        assertThat(tips)
                .extracting(ProfileTip::getTipText)
                .anyMatch(text -> text.contains("test signal") || text.contains("CI/CD"));
    }

    @Test
    void markTipAsResolved_stateOwnedTip_expectedResolvedFlagUpdated() {
        ProfileTip tip = new ProfileTip();
        tip.setUserId(TestConstants.USER_ID);
        tip.setProfileType(ProfileType.CV);
        tip.setSourceEntityId(UUID.randomUUID());
        tip.setTipText("Improve keywords");
        tip.setPriority(TipPriority.MEDIUM);
        tip.setIsResolved(false);
        tip.setCreatedAt(Instant.now());
        ProfileTip savedTip = profileTipRepository.save(tip);

        profileTipService.markTipAsResolved(savedTip.getId(), TestConstants.USER_ID);

        ProfileTip refreshed = profileTipRepository.findById(savedTip.getId()).orElseThrow();
        assertThat(refreshed.getIsResolved()).isTrue();

        List<ProfileTipResponse> unresolvedTips = profileTipService.getTipsForUser(TestConstants.USER_ID);
        assertThat(unresolvedTips)
                .extracting(ProfileTipResponse::id)
                .doesNotContain(savedTip.getId());
    }

    private CandidateCV persistCv(int atsScore, ProcessingStatus parseStatus, String parsedContent) {
        CandidateCV cv = new CandidateCV();
        cv.setUserId(TestConstants.USER_ID);
        cv.setOriginalFileUrl("temp/cv-uploads/tips.pdf");
        cv.setOriginalFileName("tips.pdf");
        cv.setFileFormat(FileFormat.PDF);
        cv.setParsedContent(parsedContent);
        cv.setParseStatus(parseStatus);
        cv.setAtsScore(atsScore);
        cv.setIsActive(true);
        cv.setUploadedAt(Instant.now());
        cv.setUpdatedAt(Instant.now());
        return candidateCVRepository.save(cv);
    }

    private void persistLinkedIn(int score, String optimizedHeadline, String optimizedSkills) {
        LinkedInProfile profile = new LinkedInProfile();
        profile.setUserId(TestConstants.USER_ID);
        profile.setProfileUrl("https://linkedin.com/in/tips-user");
        profile.setRawContent("Java Spring profile with recommendations");
        profile.setScrapeStatus(ProcessingStatus.COMPLETED);
        profile.setGlobalScore(score);
        profile.setSectionScoresJson("{\"headline\":50,\"summary\":60}");
        profile.setCreatedAt(Instant.now());
        profile.setOptimizedHeadline(optimizedHeadline);
        profile.setOptimizedSkills(optimizedSkills);
        profile.setAnalyzedAt(Instant.now());
        linkedInProfileRepository.save(profile);
    }

    private GitHubProfile persistGitHubProfile(int overallScore, String username) {
        GitHubProfile profile = new GitHubProfile();
        profile.setUserId(TestConstants.USER_ID);
        profile.setGithubUsername(username);
        profile.setOverallScore(overallScore);
        profile.setRepoCount(2);
        profile.setAuditStatus(ProcessingStatus.COMPLETED);
        profile.setCreatedAt(Instant.now());
        profile.setAnalyzedAt(Instant.now());
        return gitHubProfileRepository.save(profile);
    }

    private void persistGitHubRepository(GitHubProfile profile, String name, boolean hasTests, boolean hasCiCd, int overallScore) {
        GitHubRepository repository = new GitHubRepository();
        repository.setGithubProfile(profile);
        repository.setRepoName(name);
        repository.setRepoUrl("https://github.com/example/" + name);
        repository.setLanguage("Java");
        repository.setStars(5);
        repository.setForksCount(0);
        repository.setIsForked(false);
        repository.setReadmeScore(45);
        repository.setHasTests(hasTests);
        repository.setHasCiCd(hasCiCd);
        repository.setCodeStructureScore(50);
        repository.setDetectedIssues("No test signal detected");
        repository.setUpdatedAt(Instant.now());
        repository.setOverallScore(overallScore);
        gitHubRepositoryRepository.save(repository);
    }
}
