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
import tn.esprit.msprofile.dto.response.GitHubProfileResponse;
import tn.esprit.msprofile.dto.response.GitHubRepositoryResponse;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.GitHubProfileService;
import tn.esprit.msprofile.service.GitHubRepositoryService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tn.esprit.msprofile.testsupport.TestConstants.USER_ID;

@WebMvcTest(controllers = GitHubWorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
class GitHubProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitHubProfileService gitHubProfileService;

    @MockBean
    private GitHubRepositoryService gitHubRepositoryService;

    @MockBean
    private StaticUserContext staticUserContext;

    @BeforeEach
    void setUp() {
        when(staticUserContext.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void audit_stateValidPayload_expectedOk() throws Exception {
        GitHubProfileResponse response = sampleProfileResponse();
        when(gitHubProfileService.auditGitHubProfile(eq(USER_ID), eq("jane-doe"))).thenReturn(response);

        mockMvc.perform(post("/api/github/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUsername\":\"jane-doe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("jane-doe"))
                .andExpect(jsonPath("$.auditStatus").value("COMPLETED"));
    }

    @Test
    void audit_stateInvalidPayload_expectedBadRequest() throws Exception {
        mockMvc.perform(post("/api/github/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubUsername\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request payload"));
    }

    @Test
    void getGitHubProfile_stateProfileExists_expectedOk() throws Exception {
        when(gitHubProfileService.getGitHubProfileForUser(eq(USER_ID))).thenReturn(sampleProfileResponse());

        mockMvc.perform(get("/api/github"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallScore").value(84));
    }

    @Test
    void getGitHubProfile_stateProfileMissing_expectedNotFound() throws Exception {
        when(gitHubProfileService.getGitHubProfileForUser(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("github profile missing"));

        mockMvc.perform(get("/api/github"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("github profile missing"));
    }

    @Test
    void reaudit_stateProfileExists_expectedOk() throws Exception {
        when(gitHubProfileService.reauditProfile(eq(USER_ID))).thenReturn(sampleProfileResponse());

        mockMvc.perform(post("/api/github/reaudit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repoCount").value(2));
    }

    @Test
    void reaudit_stateProfileMissing_expectedNotFound() throws Exception {
        when(gitHubProfileService.reauditProfile(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("reaudit target missing"));

        mockMvc.perform(post("/api/github/reaudit"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("reaudit target missing"));
    }

    @Test
    void getAuditedRepositories_stateRepositoriesExist_expectedOk() throws Exception {
        GitHubProfileResponse profile = sampleProfileResponse();
        when(gitHubProfileService.getGitHubProfileForUser(eq(USER_ID))).thenReturn(profile);
        when(gitHubRepositoryService.findByGithubProfileId(eq(profile.id()))).thenReturn(List.of(sampleRepositoryResponse(profile.id())));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repoName").value("smart-api-ci-tests"));
    }

    @Test
    void getAuditedRepositories_stateProfileMissing_expectedNotFound() throws Exception {
        when(gitHubProfileService.getGitHubProfileForUser(eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("profile unavailable"));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("profile unavailable"));
    }

    private GitHubProfileResponse sampleProfileResponse() {
        return new GitHubProfileResponse(
                UUID.randomUUID(),
                USER_ID,
                "jane-doe",
                                "https://github.com/jane-doe",
                84,
                2,
                "[\"Java\",\"TypeScript\"]",
                78,
                "Strong profile, improve documentation consistency.",
                ProcessingStatus.COMPLETED,
                null,
                Instant.now(),
                                Instant.now(),
                                List.of()
        );
    }

    private GitHubRepositoryResponse sampleRepositoryResponse(UUID profileId) {
        return new GitHubRepositoryResponse(
                UUID.randomUUID(),
                profileId,
                "smart-api-ci-tests",
                "https://github.com/jane-doe/smart-api-ci-tests",
                "Java",
                24,
                4,
                false,
                82,
                true,
                true,
                85,
                null,
                Instant.now(),
                87
        );
    }
}
