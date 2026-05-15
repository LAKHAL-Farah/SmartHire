package tn.esprit.msprofile.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.nvidia")
public record NvidiaProperties(
        String apiKey,
        String baseUrl,
        String model,
        int maxTokens,
        double temperature,
        double topP,
        int timeoutSeconds
) {
}
