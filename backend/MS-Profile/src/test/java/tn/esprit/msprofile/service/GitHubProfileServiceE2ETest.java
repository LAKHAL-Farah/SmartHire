package tn.esprit.msprofile.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.GitHubRepository;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.AuditLogRepository;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.GitHubRepositoryRepository;
import tn.esprit.msprofile.testsupport.AbstractE2ETest;
import tn.esprit.msprofile.testsupport.TestConstants;
import tn.esprit.msprofile.testsupport.TestFixtureLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GitHubProfileServiceE2ETest extends AbstractE2ETest {

    @Autowired
    private GitHubProfileService gitHubProfileService;

    @Autowired
    private GitHubProfileRepository gitHubProfileRepository;

    @Autowired
    private GitHubRepositoryRepository gitHubRepositoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void auditGitHubProfile_stateValidUsername_expectedRepositoriesPersistedAndAuditLogged() {
        String reposJson = TestFixtureLoader.readText("fixtures/github-repos.json");
        stubGitHubRepositories(TestConstants.GITHUB_USERNAME, reposJson);

        GitHubProfileResponse response = gitHubProfileService.auditGitHubProfile(TestConstants.USER_ID, TestConstants.GITHUB_USERNAME);

        assertThat(response.auditStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(response.repoCount()).isEqualTo(2);
        assertThat(response.overallScore()).isNotNull();
        assertThat(response.topLanguages()).contains("Java");

        List<GitHubRepository> repos = gitHubRepositoryRepository.findByGithubProfileId(response.id());
        assertThat(repos).hasSize(2);
        assertThat(repos)
                .extracting(GitHubRepository::getOverallScore)
                .allMatch(score -> score != null && score > 0);

        List<AuditLog> logs = auditLogRepository.findByUserIdAndOperationType(TestConstants.USER_ID, OperationType.GITHUB_AUDIT);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(logs.get(0).getTokensUsed()).isGreaterThan(0);
    }

    @Test
    void computeProfileScore_stateNoRepositories_expectedFallbackToProfileReadmeScore() {
        GitHubProfile profile = new GitHubProfile();
        profile.setUserId(TestConstants.USER_ID);
        profile.setGithubUsername("no-repos-user");
        profile.setProfileReadmeScore(55);
        profile.setAuditStatus(ProcessingStatus.PENDING);
        GitHubProfile saved = gitHubProfileRepository.save(profile);

        int score = gitHubProfileService.computeProfileScore(saved.getId());

        assertThat(score).isEqualTo(55);
        GitHubProfile refreshed = gitHubProfileRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.getOverallScore()).isEqualTo(55);
    }

    @Test
    void reauditProfile_stateExistingProfile_expectedRepositoriesReplacedWithLatestSnapshot() {
        String firstSnapshot = TestFixtureLoader.readText("fixtures/github-repos.json");
        stubGitHubRepositories(TestConstants.GITHUB_USERNAME, firstSnapshot);

        GitHubProfileResponse firstAudit = gitHubProfileService.auditGitHubProfile(TestConstants.USER_ID, TestConstants.GITHUB_USERNAME);
        assertThat(firstAudit.repoCount()).isEqualTo(2);

        String secondSnapshot = """
                [
                  {
                    \"name\": \"single-repo-refresh\",
                    \"html_url\": \"https://github.com/jane-doe/single-repo-refresh\",
                    \"language\": \"Go\",
                    \"stargazers_count\": 3,
                    \"forks_count\": 0,
                    \"fork\": false,
                    \"pushed_at\": \"2026-02-10T08:00:00Z\",
                    \"description\": \"go service with ci and test setup\",
                    \"archived\": false
                  }
                ]
                """;
        stubGitHubRepositories(TestConstants.GITHUB_USERNAME, secondSnapshot);

        GitHubProfileResponse secondAudit = gitHubProfileService.reauditProfile(TestConstants.USER_ID);

        assertThat(secondAudit.repoCount()).isEqualTo(1);
        List<GitHubRepository> latestRepos = gitHubRepositoryRepository.findByGithubProfileId(secondAudit.id());
        assertThat(latestRepos).hasSize(1);
        assertThat(latestRepos.get(0).getRepoName()).isEqualTo("single-repo-refresh");
    }

    @Test
    void auditGitHubProfile_stateBlankUsername_expectedIllegalArgumentException() {
        assertThatThrownBy(() -> gitHubProfileService.auditGitHubProfile(TestConstants.USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
