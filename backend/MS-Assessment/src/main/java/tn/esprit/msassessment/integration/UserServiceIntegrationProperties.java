package tn.esprit.msassessment.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound hook to MS-User (or any service) when an assessment result is published to the candidate.
 * MS-User does not need to change until you add a matching controller; until then keep {@code enabled=false}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smarthire.integration.user-service")
public class UserServiceIntegrationProperties {

    /** When true, POST to {@link #baseUrl} + {@link #path} after a result is published (auto or manual). */
    private boolean enabled = false;

    /** Example: {@code http://localhost:8082} */
    private String baseUrl = "";

    /** Path appended to base URL, e.g. {@code /api/internal/assessment-result-published} */
    private String path = "/api/internal/assessment-result-published";

    /** When true, admin session lists resolve {@code candidateDisplayName} via GET {@code /api/v1/users/{id}} on MS-User. */
    private boolean lookupEnabled = true;

    /** Score &gt;= this value sets {@code passed=true} in the JSON body. */
    private int passingScorePercent = 70;

    private int connectTimeoutMillis = 3000;

    private int readTimeoutMillis = 5000;
}
