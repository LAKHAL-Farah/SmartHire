package tn.esprit.msprofile.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@ActiveProfiles("test")
public abstract class AbstractE2ETest {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    protected static final WireMockServer WIREMOCK = new WireMockServer(9561);

    static {
        WIREMOCK.start();
    }

    @BeforeAll
    static void ensureWiremockStarted() {
        if (!WIREMOCK.isRunning()) {
            WIREMOCK.start();
        }
    }

    @AfterAll
    static void shutdownWiremock() {
        if (WIREMOCK.isRunning()) {
            WIREMOCK.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        if (!WIREMOCK.isRunning()) {
            WIREMOCK.start();
        }
        WIREMOCK.resetAll();
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("TRUNCATE TABLE cv_version");
            jdbcTemplate.execute("TRUNCATE TABLE github_repository");
            jdbcTemplate.execute("TRUNCATE TABLE candidate_cv");
            jdbcTemplate.execute("TRUNCATE TABLE job_offer");
            jdbcTemplate.execute("TRUNCATE TABLE linkedin_profile");
            jdbcTemplate.execute("TRUNCATE TABLE github_profile");
            jdbcTemplate.execute("TRUNCATE TABLE hire_readiness_score");
            jdbcTemplate.execute("TRUNCATE TABLE profile_tip");
            jdbcTemplate.execute("TRUNCATE TABLE audit_log");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String dbName = "ms_profile_e2e_" + java.util.UUID.randomUUID().toString().replace("-", "");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
        registry.add("app.async-processing.enabled", () -> "false");
        registry.add("app.github-api-base-url", () -> "http://localhost:" + WIREMOCK.port());
    }

    protected void stubGitHubRepositories(String username, String jsonResponse) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/users/" + username + "/repos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));
    }

    protected void stubLinkedInProfilePage(String path, String htmlResponse) {
        WIREMOCK.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(htmlResponse)));
    }

    protected void stubGpt4oSuccess(String jsonResponse) {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/gpt4o"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));
    }

    protected void stubAnthropicSuccess(String jsonResponse) {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("test-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));
    }
}
