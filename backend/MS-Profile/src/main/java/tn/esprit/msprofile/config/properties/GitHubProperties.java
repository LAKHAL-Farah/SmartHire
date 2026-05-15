package tn.esprit.msprofile.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github")
public record GitHubProperties(
        String apiBaseUrl,
        String token,
        int reposPerPage,
        int maxPages,
        int readmeMaxChars,
        int connectionTimeoutSeconds,
        int readTimeoutSeconds
) {
}
