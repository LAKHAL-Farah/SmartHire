package tn.esprit.msprofile.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tn.esprit.msprofile.config.properties.FileStorageProperties;
import tn.esprit.msprofile.config.properties.GitHubProperties;
import tn.esprit.msprofile.config.properties.NvidiaProperties;
import tn.esprit.msprofile.config.properties.OpenAiProperties;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties({FileStorageProperties.class, OpenAiProperties.class, NvidiaProperties.class, GitHubProperties.class})
public class AppConfig {

    @Bean(name = "openAiWebClient")
    public WebClient openAiWebClient(OpenAiProperties openAiProperties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, openAiProperties.getTimeoutSeconds() * 1000)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(openAiProperties.getTimeoutSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(openAiProperties.getTimeoutSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl(openAiProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean(name = "nvidiaWebClient")
    public WebClient nvidiaWebClient(NvidiaProperties nvidiaProperties) {
        int nvidiaTimeout = Math.max(10, nvidiaProperties.timeoutSeconds());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(nvidiaTimeout, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl(nvidiaProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nvidiaProperties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
