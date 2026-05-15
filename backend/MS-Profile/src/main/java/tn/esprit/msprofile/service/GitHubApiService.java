package tn.esprit.msprofile.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tn.esprit.msprofile.config.properties.GitHubProperties;
import tn.esprit.msprofile.exception.ExternalApiException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubApiService {

    private final @Qualifier("githubWebClient") WebClient githubWebClient;
    private final GitHubProperties gitHubProperties;
    private final ObjectMapper objectMapper;

    public List<GhRepo> fetchPublicRepos(String username) {
        List<GhRepo> allRepos = new ArrayList<>();
        int page = 1;
        while (page <= gitHubProperties.maxPages()) {
            List<GhRepo> pageRepos = fetchReposPage(username, page);
            allRepos.addAll(pageRepos);
            if (pageRepos.size() < gitHubProperties.reposPerPage()) {
                break;
            }
            page++;
        }
        return allRepos;
    }

    private List<GhRepo> fetchReposPage(String username, int page) {
        try {
            return githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users/{username}/repos")
                            .queryParam("type", "public")
                            .queryParam("per_page", gitHubProperties.reposPerPage())
                            .queryParam("page", page)
                            .queryParam("sort", "pushed")
                            .queryParam("direction", "desc")
                            .build(username))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GhRepo>>() {
                    })
                    .block(Duration.ofSeconds(15));
        } catch (WebClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404) {
                throw new ResourceNotFoundException("GitHub user '" + username + "' not found");
            }
            if (status.value() == 403 || status.value() == 429) {
                throw new ExternalApiException(
                        "GitHub API rate limit exceeded. Set the GITHUB_TOKEN environment variable for 5000 requests/hour instead of 60. Get a token at https://github.com/settings/tokens"
                );
            }
            throw new ExternalApiException("GitHub API error fetching repos: HTTP " + status.value());
        } catch (RuntimeException e) {
            if (isTimeout(e)) {
                throw new ExternalApiException("GitHub API timed out fetching repos for user: " + username);
            }
            throw e;
        }
    }

    public Optional<String> fetchReadme(String username, String repoName) {
        try {
            GhReadme readme = githubWebClient.get()
                    .uri("/repos/{username}/{repo}/readme", username, repoName)
                    .retrieve()
                    .bodyToMono(GhReadme.class)
                    .block(Duration.ofSeconds(15));
            if (readme == null || readme.content() == null || readme.content().isBlank()) {
                return Optional.empty();
            }

            String decoded = new String(
                    Base64.getMimeDecoder().decode(readme.content()),
                    StandardCharsets.UTF_8
            );
            int max = gitHubProperties.readmeMaxChars();
            if (decoded.length() > max) {
                decoded = decoded.substring(0, max) + "...";
            }
            return Optional.of(decoded);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("Could not fetch README for {}/{}: {}", username, repoName, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not fetch README for {}/{}: {}", username, repoName, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean checkCiCdPresence(String username, String repoName) {
        try {
            List<GhContent> workflows = githubWebClient.get()
                    .uri("/repos/{username}/{repo}/contents/.github/workflows", username, repoName)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GhContent>>() {
                    })
                    .block(Duration.ofSeconds(15));
            return workflows != null && !workflows.isEmpty();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            log.warn("Could not check CI/CD for {}/{}: {}", username, repoName, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Could not check CI/CD for {}/{}: {}", username, repoName, e.getMessage());
            return false;
        }
    }

    public boolean checkTestPresence(String username, String repoName) {
        String[] paths = new String[]{
                "src/test",
                "tests",
                "__tests__",
                "spec",
                "test",
                "src/tests",
                "__specs__"
        };

        for (String path : paths) {
            try {
                githubWebClient.get()
                        .uri("/repos/{username}/{repo}/contents/{path}", username, repoName, path)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<GhContent>>() {
                        })
                        .block(Duration.ofSeconds(15));
                return true;
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 404) {
                    continue;
                }
                log.warn("Could not check test path '{}' for {}/{}: {}", path, username, repoName, e.getMessage());
            } catch (Exception e) {
                log.warn("Could not check test path '{}' for {}/{}: {}", path, username, repoName, e.getMessage());
            }
        }

        return false;
    }

    public Optional<String> fetchProfileReadme(String username) {
        return fetchReadme(username, username);
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhRepo(
            long id,
            String name,
            @JsonProperty("full_name") String fullName,
            String description,
            @JsonProperty("html_url") String htmlUrl,
            String language,
            @JsonProperty("stargazers_count") int stargazersCount,
            @JsonProperty("forks_count") int forksCount,
            boolean fork,
            boolean archived,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("pushed_at") String pushedAt,
            GhOwner owner
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhOwner(
            String login,
            @JsonProperty("html_url") String htmlUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhReadme(
            String name,
            String encoding,
            String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GhContent(
            String name,
            String type,
            String path
    ) {
    }
}
