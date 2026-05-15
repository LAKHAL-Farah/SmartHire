package tn.esprit.msassessment.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tn.esprit.msassessment.entity.AssessmentSession;

import java.time.Duration;

/**
 * Fire-and-forget HTTP notification carrying the candidate {@code userId} (UUID) and session metadata.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceIntegrationClient {

    private static final String EVENT_TYPE = "assessment.result.published";

    private final UserServiceIntegrationProperties properties;

    public void notifyResultPublished(AssessmentSession session) {
        if (!properties.isEnabled()) {
            return;
        }
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("smarthire.integration.user-service.enabled=true but base-url is empty; skipping notification");
            return;
        }
        if (!session.isResultReleasedToCandidate()) {
            return;
        }

        Integer score = session.getScorePercent();
        int passAt = properties.getPassingScorePercent();
        boolean passed = score != null && score >= passAt;

        AssessmentResultPublishedPayload body = new AssessmentResultPublishedPayload(
                EVENT_TYPE,
                session.getUserId().toString(),
                session.getId(),
                session.getCategory().getId(),
                score,
                passed
        );

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));

        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(base.trim())
                .build();

        String path = properties.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }

        try {
            client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Notified user-service integration for sessionId={} userId={}", session.getId(), session.getUserId());
        } catch (RestClientException ex) {
            log.warn("User-service integration POST failed (sessionId={}): {}", session.getId(), ex.getMessage());
        }
    }
}
