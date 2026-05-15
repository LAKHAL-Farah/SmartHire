package tn.esprit.msassessment.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;
import tn.esprit.msassessment.config.AssessmentCategorySeedService;
import tn.esprit.msassessment.service.CandidateAssignmentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demo initializer: assigns sensible assessment categories to seeded demo users.
 * Runs at startup and will try to resolve demo users from MS-User and create
 * APPROVED assignment rows for them so the admin UI and candidate flows have data.
 */
@Component
@RequiredArgsConstructor
@Order(200)
@Slf4j
public class AssessmentDemoInitializer implements ApplicationRunner {

    private final AssessmentCategorySeedService seedService;
    private final QuestionCategoryRepository categoryRepository;
    private final CandidateAssignmentService candidateAssignmentService;
    private final String userServiceBase = "http://localhost:8082/api/v1/users/email/";

    @Override
    public void run(ApplicationArguments args) {
        // Ensure categories exist first
        seedService.seedAllMissing();

        RestTemplate rt = new RestTemplate();

        Map<String, List<String>> mapping = Map.of(
                "amin.khalil@smarthire.tn", List.of("AWS_CLOUD", "DOCKER_K8S", "DEVOPS_ADVANCED"),
                "mariem.benali@smarthire.tn", List.of("MACHINE_LEARNING", "PYTHON_CORE", "GENAI_LLM"),
                "ons.jemai@smarthire.tn", List.of("JS_TS_WEB", "NODE_BACKEND", "REACT_WEB")
        );

        for (Map.Entry<String, List<String>> e : mapping.entrySet()) {
            String email = e.getKey();
            try {
                @SuppressWarnings("unchecked")
                var resp = rt.getForObject(userServiceBase + email, java.util.Map.class);
                if (resp == null || !resp.containsKey("id")) {
                    log.warn("Could not resolve user {}: empty response", email);
                    continue;
                }
                UUID userId = UUID.fromString(resp.get("id").toString());

                List<Long> categoryIds = new ArrayList<>();
                for (String code : e.getValue()) {
                    categoryRepository.findByCode(code).ifPresent(c -> categoryIds.add(c.getId()));
                }

                if (categoryIds.isEmpty()) {
                    log.warn("No categories found for user {} (codes {}). Skipping.", email, e.getValue());
                    continue;
                }

                candidateAssignmentService.adminAssignAssessment(userId, categoryIds, null, null, true);
                log.info("Assigned {} categories to demo user {}", categoryIds.size(), email);
            } catch (RestClientException ex) {
                log.warn("Failed to contact MS-User to resolve {}: {}", email, ex.getMessage());
            } catch (Exception ex) {
                log.error("Error while assigning demo categories to {}: {}", email, ex.getMessage(), ex);
            }
        }
    }
}
