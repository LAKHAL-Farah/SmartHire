package tn.esprit.msprofile.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tn.esprit.msprofile.config.properties.GitHubProperties;

import java.time.Duration;

@Configuration
public class GitHubConfig {

    @Bean
    @Qualifier("githubWebClient")
    public WebClient githubWebClient(GitHubProperties props) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.connectionTimeoutSeconds() * 1000)
                .responseTimeout(Duration.ofSeconds(props.readTimeoutSeconds()));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(props.apiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));

        String token = props.token();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }

        return builder.build();
    }
}
