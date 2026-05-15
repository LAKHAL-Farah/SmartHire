package tn.esprit.msassessment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only calls to MS-User to show candidate names on assessment admin screens.
 * Uses the application {@link ObjectMapper} so JSON matches MS-User payloads, then falls back to the profile API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MsUserLookupClient {

    private final UserServiceIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public Optional<String> findDisplayName(UUID userId) {
        if (!properties.isLookupEnabled()) {
            return Optional.empty();
        }
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("Candidate name lookup skipped: smarthire.integration.user-service.base-url is empty");
            return Optional.empty();
        }
        RestClient client = buildClient();

        Optional<String> fromUser = fetchUserDisplayName(client, userId);
        if (fromUser.isPresent()) {
            return fromUser;
        }
        return fetchProfileDisplayName(client, userId);
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.getBaseUrl().trim())
                .build();
    }

    private Optional<String> fetchUserDisplayName(RestClient client, UUID userId) {
        try {
            String json = client.get()
                    .uri("/api/v1/users/{id}", userId)
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            MsUserApiDto dto = objectMapper.readValue(json, MsUserApiDto.class);
            return Optional.ofNullable(MsUserApiDto.formatDisplayName(dto)).filter(s -> !s.isBlank());
        } catch (RestClientResponseException ex) {
            if (!ex.getStatusCode().is4xxClientError()) {
                log.warn("MS-User GET /api/v1/users/{} failed: {} {}", userId, ex.getStatusCode(), ex.getMessage());
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("MS-User GET /api/v1/users/{} unreachable: {}", userId, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("MS-User user JSON parse failed for {}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchProfileDisplayName(RestClient client, UUID userId) {
        try {
            String json = client.get()
                    .uri("/api/v1/profiles/user/{userId}", userId)
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            MsProfileApiDto dto = objectMapper.readValue(json, MsProfileApiDto.class);
            return Optional.ofNullable(formatProfileDisplayName(dto)).filter(s -> !s.isBlank());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.debug("No MS-User profile for {}", userId);
            } else {
                log.warn("MS-User GET /api/v1/profiles/user/{} failed: {}", userId, ex.getStatusCode());
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.debug("MS-User profile lookup failed for {}: {}", userId, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("MS-User profile JSON parse failed for {}: {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String formatProfileDisplayName(MsProfileApiDto p) {
        if (p == null) {
            return null;
        }
        String fn = p.firstName() != null ? p.firstName().trim() : "";
        String ln = p.lastName() != null ? p.lastName().trim() : "";
        String combined = (fn + " " + ln).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        if (p.email() != null && !p.email().isBlank()) {
            return p.email().trim();
        }
        return null;
    }
}
