package tn.esprit.msroadmap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean(name = "aiWebClient")
    public WebClient aiWebClient(
            @Value("${nvidia.api.key:nvapi-placeholder}") String apiKey,
            @Value("${nvidia.api.url:https://integrate.api.nvidia.com/v1}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    @Bean(name = "githubWebClient")
    public WebClient githubWebClient(
            @Value("${github.api.token:placeholder}") String token,
            @Value("${github.api.base-url:https://api.github.com}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .build();
    }

    @Bean(name = "genericWebClient")
    public WebClient genericWebClient() {
        return WebClient.builder().build();
    }
}
