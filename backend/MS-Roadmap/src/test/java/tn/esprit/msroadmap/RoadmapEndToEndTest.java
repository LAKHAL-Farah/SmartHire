package tn.esprit.msroadmap;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tn.esprit.msroadmap.ai.AiClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(RoadmapEndToEndTest.TestAiConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoadmapEndToEndTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${local.server.port}")
    int port;

    static Long roadmapId;
    static Long stepId;
    static Long resourceId;
    static Long notificationId;
    static String shareToken;

    @Test @Order(1)
    void createRoadmap_shouldReturn201() {
        Map<String,Object> request = Map.of(
                "userId", 1,
                "careerPathId", 10,
                "title", "Backend Java Roadmap",
                "difficulty", "INTERMEDIATE",
                "estimatedWeeks", 8,
                "steps", List.of(
                        Map.of("title", "Learn Spring Boot", "stepOrder", 1, "estimatedDays", 7),
                        Map.of("title", "Build REST APIs", "stepOrder", 2, "estimatedDays", 6)
                )
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/roadmaps"), request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("title")).isEqualTo("Backend Java Roadmap");
        roadmapId = Long.valueOf(response.getBody().get("id").toString());
    }

    @Test @Order(2)
    void getRoadmapById_shouldReturn200() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            url("/api/roadmaps/" + roadmapId), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id")).isEqualTo(roadmapId.intValue());
    }

    @Test @Order(3)
    void getRoadmapByUserId_shouldReturn200Or404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            url("/api/roadmaps/user/1"), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(4)
    void getAllRoadmaps_shouldReturn200() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            url("/api/roadmaps"), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @Order(5)
    void getRoadmapsByCareerPath_shouldReturn200Or404() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            url("/api/roadmaps/career-path/10"), List.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(6)
    void getProgressSummary_shouldReturn200Or404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/roadmaps/" + roadmapId + "/progress-summary"), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(7)
    void getPace_shouldReturn200Or404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/roadmaps/" + roadmapId + "/pace"), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(8)
    void pauseRoadmap_shouldReturn200OrNotFound() {
        ResponseEntity<Map> response = restTemplate.exchange(
            url("/api/roadmaps/" + roadmapId + "/pause"),
                HttpMethod.PUT, null, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(9)
    void resumeRoadmap_shouldReturn200OrNotFound() {
        ResponseEntity<Map> response = restTemplate.exchange(
            url("/api/roadmaps/" + roadmapId + "/resume"),
                HttpMethod.PUT, null, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(10)
    void shareRoadmap_shouldReturn200WithTokenOrNotFound() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/roadmaps/" + roadmapId + "/share"), null, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            shareToken = response.getBody().get("shareToken").toString();
            assertThat(shareToken).isNotBlank();
        }
    }

    @Test @Order(11)
    void getPublicRoadmapByToken_shouldReturn200OrNotFound() {
        if (shareToken == null) return; // skip
        ResponseEntity<Map> response = restTemplate.getForEntity(
            url("/api/roadmaps/public/" + shareToken), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(12)
    void getPublicRoadmapEmbed_shouldReturn200OrNotFound() {
        if (shareToken == null) return;
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/roadmaps/public/" + shareToken + "/embed"), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(13)
    void unshareRoadmap_shouldReturn204OrNotFound() {
        ResponseEntity<Void> response = restTemplate.exchange(
            url("/api/roadmaps/" + roadmapId + "/share"),
                HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.NOT_FOUND);
    }

    @Test @Order(14)
    void getStepsByRoadmapId_shouldReturn200AndStoreStepId() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            url("/api/roadmap-steps/roadmap/" + roadmapId), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        if (response.getBody() != null && !response.getBody().isEmpty()) {
            Object first = response.getBody().get(0);
            if (first instanceof Map<?, ?> firstMap) {
                stepId = toLong(firstMap.get("id"));
            }
        }
        assertThat(stepId).isNotNull();
    }

    @Test @Order(15)
    void addStep_shouldReturn200Or404() {
        Map<String,Object> request = Map.of(
                "title", "Testing Strategy",
                "estimatedDays", 3,
                "stepOrder", 3
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/roadmap-steps/" + roadmapId),
                request, Map.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(16)
    void createMilestone_shouldReturn201Or404() {
        Map<String, Object> request = Map.of(
                "title", "Milestone 1",
                "description", "Complete first step",
                "stepThreshold", 1
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/api/milestones/roadmap/" + roadmapId), request, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.NOT_FOUND);
    }

    @Test @Order(17)
    void completeStep_shouldUnlockNextStepOrNotFound() {
        if (stepId == null) return;
        ResponseEntity<Map> response = restTemplate.exchange(
            url("/api/roadmap-steps/" + stepId + "/complete?userId=1"),
                HttpMethod.PUT, null, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(18)
    void getMilestones_shouldReturn200() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            url("/api/milestones/roadmap/" + roadmapId),
                List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @Order(19)
    void getNextMilestone_shouldReturn200() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/milestones/roadmap/" + roadmapId + "/next"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @Order(20)
    void searchResources_shouldReturn200() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                url("/api/step-resources/search?topic=Spring&provider=UDEMY"), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test @Order(21)
    void addResource_shouldReturn201Or404() {
        Map<String,Object> dto = Map.of(
                "type", "COURSE",
                "provider", "UDEMY",
                "title", "Spring Boot Masterclass",
                "url", "https://udemy.com/spring-boot",
                "isFree", false
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/step-resources/step/" + stepId), dto, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.NOT_FOUND);
        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            resourceId = toLong(response.getBody().get("id"));
        }
    }

    @Test @Order(22)
    void getResourcesByStepId_shouldReturn200() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            url("/api/step-resources/step/" + stepId), List.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(23)
    void syncResources_shouldReturn202Or404() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                url("/api/step-resources/step/" + stepId + "/sync"), null, Void.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.ACCEPTED, HttpStatus.NOT_FOUND);
    }

    @Test @Order(24)
    void deleteResource_shouldReturn204OrNotFound() {
        if (resourceId == null) return;
        ResponseEntity<Void> response = restTemplate.exchange(
            url("/api/step-resources/" + resourceId),
                HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.NOT_FOUND);
    }

    @Test @Order(25)
    void notificationsEndpoints_shouldReturnExpectedStatuses() {
        ResponseEntity<List> listByUser = restTemplate.getForEntity(url("/api/notifications/user/1"), List.class);
        assertThat(listByUser.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> listByRoadmap = restTemplate.getForEntity(url("/api/notifications/roadmap/" + roadmapId + "?userId=1"), List.class);
        assertThat(listByRoadmap.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> unread = restTemplate.getForEntity(url("/api/notifications/user/1/unread-count"), Map.class);
        assertThat(unread.getStatusCode()).isEqualTo(HttpStatus.OK);

        if (listByUser.getBody() != null && !listByUser.getBody().isEmpty() && listByUser.getBody().get(0) instanceof Map<?, ?> first) {
            notificationId = toLong(first.get("id"));
        }
    }

    @Test @Order(26)
    void markNotifications_shouldReturnNoContentOrOk() {
        if (notificationId != null) {
            ResponseEntity<Map> markOne = restTemplate.exchange(
                    url("/api/notifications/" + notificationId + "/read"), HttpMethod.PUT, null, Map.class);
            assertThat(markOne.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<Void> markAll = restTemplate.exchange(
                url("/api/notifications/user/1/read-all"), HttpMethod.PUT, null, Void.class);
        assertThat(markAll.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        if (notificationId != null) {
            ResponseEntity<Void> delete = restTemplate.exchange(
                    url("/api/notifications/" + notificationId), HttpMethod.DELETE, null, Void.class);
            assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Test @Order(27)
    void streak_shouldReturn200Or404() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/streaks/user/1/roadmap/" + roadmapId), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(28)
    void roadmapVisualEndpoints_shouldReturnExpectedStatuses() {
        ResponseEntity<Map> graph = restTemplate.getForEntity(url("/api/roadmaps/visual/" + roadmapId + "/graph"), Map.class);
        assertThat(graph.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);

        ResponseEntity<String> startInvalid = exchangeSafely("/api/roadmaps/visual/nodes/999999/start?userId=1", HttpMethod.PUT, null);
        assertThat(startInvalid.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> completeInvalid = exchangeSafely("/api/roadmaps/visual/nodes/999999/complete?userId=1", HttpMethod.PUT, null);
        assertThat(completeInvalid.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test @Order(29)
    void roadmapGenerationEndpoints_shouldValidateInput() {
        ResponseEntity<String> generateRoadmap = postSafely("/api/roadmaps/generate", Map.of(
            "userId", 1,
            "careerPathId", 10,
            "careerPathName", "Backend Java",
            "skillGaps", List.of("Spring Security"),
            "strongSkills", List.of("Java"),
            "experienceLevel", "INTERMEDIATE",
            "weeklyHoursAvailable", 10
        ));
        assertThat(generateRoadmap.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> generateVisual = postSafely("/api/roadmaps/visual/generate", Map.of(
            "userId", 1,
            "careerPathId", 10,
            "careerPathName", "Backend Java",
            "skillGaps", List.of("Spring Security"),
            "strongSkills", List.of("Java"),
            "experienceLevel", "INTERMEDIATE",
            "weeklyHoursAvailable", 10
        ));
        assertThat(generateVisual.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> replan = postSafely(
            "/api/roadmaps/visual/" + roadmapId + "/replan",
            Map.of("roadmapId", roadmapId, "newSkillGaps", List.of("Docker"), "newStrongSkills", List.of("Java"), "experienceLevel", "INTERMEDIATE")
        );
        assertThat(replan.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test @Order(30)
    void updateRoadmap_shouldReturn200Or404() {
        Map<String, Object> update = Map.of(
                "userId", 1,
                "careerPathId", 10,
                "title", "Backend Java Roadmap Updated",
                "difficulty", "INTERMEDIATE",
                "estimatedWeeks", 9,
                "steps", List.of(
                        Map.of("title", "Learn Spring Boot", "stepOrder", 1, "estimatedDays", 7)
                )
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/roadmaps/" + roadmapId), HttpMethod.PUT, new HttpEntity<>(update), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test @Order(31)
    void deleteRoadmap_shouldReturn204OrNotFound() {
        ResponseEntity<Void> response = restTemplate.exchange(
            url("/api/roadmaps/" + roadmapId),
                HttpMethod.DELETE, null, Void.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.NOT_FOUND);
    }

    @Test @Order(32)
    void getRoadmapAfterDelete_shouldReturn404() {
        ResponseEntity<String> response = exchangeSafely("/api/roadmaps/" + roadmapId, HttpMethod.GET, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private ResponseEntity<String> exchangeSafely(String path, HttpMethod method, Object body) {
        try {
            HttpEntity<?> entity = body == null ? HttpEntity.EMPTY : new HttpEntity<>(body);
            return restTemplate.exchange(url(path), method, entity, String.class);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        }
    }

    private ResponseEntity<String> postSafely(String path, Object body) {
        try {
            return restTemplate.postForEntity(url(path), body, String.class);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        }
    }

    @TestConfiguration
    static class TestAiConfig {
        @Bean
        AiClient aiClient(ObjectMapper objectMapper) {
            String roadmapJson = """
                    {
                      "title": "Backend Java Roadmap",
                      "description": "Generated roadmap",
                      "difficulty": "INTERMEDIATE",
                      "nodes": [
                        {
                          "id": "node-1",
                          "title": "Learn Spring Boot",
                          "description": "Core Spring Boot concepts",
                          "objective": "Build a simple REST API",
                          "type": "REQUIRED",
                          "difficulty": "BEGINNER",
                          "stepOrder": 1,
                          "estimatedDays": 7,
                          "technologies": "Java, Spring Boot",
                          "x": 120,
                          "y": 100
                        },
                        {
                          "id": "node-2",
                          "title": "Build REST APIs",
                          "description": "REST design and implementation",
                          "objective": "Implement CRUD endpoints",
                          "type": "REQUIRED",
                          "difficulty": "INTERMEDIATE",
                          "stepOrder": 2,
                          "estimatedDays": 10,
                          "technologies": "Spring Web, JPA",
                          "x": 120,
                          "y": 300
                        }
                      ],
                      "edges": [
                        {
                          "from": "node-1",
                          "to": "node-2",
                          "type": "REQUIRED"
                        }
                      ]
                    }
                    """;

            return new AiClient(null, objectMapper) {
                @Override
                public String call(String systemPrompt, String userPrompt) {
                    return roadmapJson;
                }

                @Override
                public String generateRoadmap(String careerPath, String skillGaps, String strongSkills,
                                              String experienceLevel, int weeklyHours) {
                    return roadmapJson;
                }
            };
        }
    }
}
