package tn.esprit.msroadmap.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.msroadmap.DTO.request.RoadmapRequest;
import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.request.StepRequest;
import tn.esprit.msroadmap.Entities.CareerPathTemplate;
import tn.esprit.msroadmap.Enums.RoadmapStatus;
import tn.esprit.msroadmap.Repositories.CareerPathTemplateRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapVisualService;

import java.time.LocalDateTime;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Locale.ROOT;

/**
 * Demo initializer: creates personalized roadmaps for demo users based on their roles.
 * Runs at startup and resolves demo users from MS-User, then creates tailored roadmaps.
 * 
 * - cloud_amin: Cloud/DevOps roadmap
 * - ai_mariem: AI/Machine Learning roadmap
 * - dev_ons: Full-stack Web Development roadmap
 */
@Component
@RequiredArgsConstructor
@Order(210)  // Runs after RoadmapDemoSeedService (200)
@Slf4j
public class RoadmapDemoInitializer implements ApplicationRunner {

    private static final String USER_SERVICE_BASE = "http://localhost:8082/api/v1/users/email/";

    private final IRoadmapVisualService roadmapVisualService;
    private final IRoadmapService roadmapService;
    private final RoadmapRepository roadmapRepository;
    private final CareerPathTemplateRepository careerPathTemplateRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting RoadmapDemoInitializer...");

        RestTemplate rt = new RestTemplate();

        Map<String, RoadmapConfig> userRoadmapMap = Map.of(
                "amin.khalil@smarthire.tn", new RoadmapConfig(
                        "Cloud Platform Engineer",
                        "Roadmap for cloud-native engineering with DevOps, Kubernetes, and AWS.",
                        List.of("Cloud Fundamentals", "Linux & Networking", "Docker", "Kubernetes", "AWS Core Services", "Infrastructure as Code", "CI/CD Pipelines", "Monitoring & Reliability"),
                        List.of("AWS", "Kubernetes", "Docker", "CI/CD", "Infrastructure as Code"),
                        List.of("Java", "Linux", "Git"),
                        "INTERMEDIATE",
                        15,
                        12
                ),
                "mariem.benali@smarthire.tn", new RoadmapConfig(
                        "AI & Machine Learning Engineer",
                        "Roadmap for practical machine learning, deep learning, and MLOps delivery.",
                        List.of("Python for ML", "Data Preprocessing", "Supervised Learning", "Model Evaluation", "Deep Learning", "NLP Foundations", "LLM Applications", "MLOps & Deployment"),
                        List.of("TensorFlow", "PyTorch", "Deep Learning", "NLP", "LLM Fine-tuning"),
                        List.of("Python", "Statistics", "SQL"),
                        "INTERMEDIATE",
                        12,
                        14
                ),
                "ons.jemai@smarthire.tn", new RoadmapConfig(
                        "Software Engineer",
                        "Roadmap for modern full-stack software engineering and production delivery.",
                        List.of("TypeScript Fundamentals", "Frontend Architecture", "Backend APIs", "Database Design", "Testing Strategy", "System Design Basics", "DevOps Workflow", "Production Readiness"),
                        List.of("React", "Node.js", "TypeScript", "Database Design", "REST APIs"),
                        List.of("JavaScript", "HTML/CSS", "Git"),
                        "INTERMEDIATE",
                        15,
                        10
                )
        );

        Map<String, Long> careerPathIdsByEmail = ensureCareerPaths(userRoadmapMap);

        for (Map.Entry<String, RoadmapConfig> entry : userRoadmapMap.entrySet()) {
            String email = entry.getKey();
            RoadmapConfig config = entry.getValue();

            try {
                @SuppressWarnings("unchecked")
                var resp = rt.getForObject(USER_SERVICE_BASE + email, java.util.Map.class);
                
                if (resp == null || !resp.containsKey("id")) {
                    log.warn("Could not resolve user {}: empty response", email);
                    continue;
                }

                String userUuid = String.valueOf(resp.get("id"));
                long userIdLong = stablePositiveIdFromString(userUuid);
                Long careerPathId = careerPathIdsByEmail.get(email);

                if (careerPathId == null) {
                    log.warn("No career path ID resolved for {}. Skipping roadmap creation.", email);
                    continue;
                }

                generateRoadmapForUser(userIdLong, email, careerPathId, config);
                
                log.info("Seeded roadmap for demo user {} with numeric userId={}", email, userIdLong);

            } catch (RestClientException ex) {
                log.warn("Failed to contact MS-User to resolve {}: {}", email, ex.getMessage());
            } catch (Exception ex) {
                log.error("Error while generating roadmap for {}: {}", email, ex.getMessage(), ex);
            }
        }

        log.info("RoadmapDemoInitializer completed.");
    }

    private Map<String, Long> ensureCareerPaths(Map<String, RoadmapConfig> mapping) {
        Map<String, Long> idsByEmail = new HashMap<>();

        for (Map.Entry<String, RoadmapConfig> entry : mapping.entrySet()) {
            RoadmapConfig cfg = entry.getValue();
            CareerPathTemplate template = careerPathTemplateRepository.findAll().stream()
                    .filter(t -> t.getTitle() != null && t.getTitle().equalsIgnoreCase(cfg.careerPathName))
                    .findFirst()
                    .orElseGet(CareerPathTemplate::new);

            template.setTitle(cfg.careerPathName);
            template.setDescription(cfg.careerPathDescription);
            template.setDefaultTopics(String.join(", ", cfg.defaultTopics));
            template.setDifficulty(cfg.experienceLevel);
            template.setEstimatedWeeks(cfg.estimatedWeeks);
            template.setPublished(true);
            if (template.getCreatedAt() == null) {
                template.setCreatedAt(LocalDateTime.now());
            }
            template.setUpdatedAt(LocalDateTime.now());

            CareerPathTemplate saved = careerPathTemplateRepository.save(template);
            idsByEmail.put(entry.getKey(), saved.getId());
        }

        return idsByEmail;
    }

    private void generateRoadmapForUser(Long userId, String email, Long careerPathId, RoadmapConfig config) {
        boolean hasActiveRoadmap = !roadmapRepository
                .findByUserIdAndStatus(userId, RoadmapStatus.ACTIVE)
                .isEmpty();

        if (hasActiveRoadmap) {
            log.info("Roadmap already exists for {} (userId={}), skipping creation.", email, userId);
            return;
        }

        try {
            RoadmapGenerationRequestDto request = RoadmapGenerationRequestDto.builder()
                    .userId(userId)
                    .careerPathId(careerPathId)
                    .careerPathName(config.careerPathName)
                    .skillGaps(config.skillGaps)
                    .strongSkills(config.strongSkills)
                    .experienceLevel(config.experienceLevel)
                    .weeklyHoursAvailable(config.weeklyHoursAvailable)
                    .preferredLanguage("EN")
                    .build();

            var response = roadmapVisualService.generateVisualRoadmap(request);

            log.info("Generated visual roadmap for {} with {} nodes",
                    email, response.getTotalNodes());

        } catch (Exception e) {
            log.warn("AI roadmap generation failed for {}. Falling back to structured static roadmap. Cause: {}", email, e.getMessage());

            List<StepRequest> fallbackSteps = new ArrayList<>();
            for (int i = 0; i < config.defaultTopics.size(); i++) {
                fallbackSteps.add(new StepRequest(config.defaultTopics.get(i), i + 1, 5));
            }

            RoadmapRequest fallbackRequest = new RoadmapRequest(
                    userId,
                    careerPathId,
                    config.careerPathName + " Roadmap",
                    config.experienceLevel,
                    config.estimatedWeeks,
                    fallbackSteps
            );

            roadmapService.createRoadmap(fallbackRequest);
            log.info("Created fallback roadmap for {}", email);
        }
    }

    private long stablePositiveIdFromString(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(ROOT);
        BigInteger hash = new BigInteger("cbf29ce484222325", 16);
        BigInteger prime = new BigInteger("100000001b3", 16);
        BigInteger mask64 = new BigInteger("ffffffffffffffff", 16);
        BigInteger maxSafe = BigInteger.valueOf(9_007_199_254_740_991L);

        for (int i = 0; i < normalized.length(); i++) {
            hash = hash.xor(BigInteger.valueOf(normalized.charAt(i)));
            hash = hash.multiply(prime).and(mask64);
        }

        BigInteger reduced = hash.mod(maxSafe);
        if (reduced.equals(BigInteger.ZERO)) {
            return 1L;
        }
        return reduced.longValue();
    }

    /**
     * Configuration for a demo user's roadmap
     */
    record RoadmapConfig(
            String careerPathName,
            String careerPathDescription,
            List<String> defaultTopics,
            List<String> skillGaps,
            List<String> strongSkills,
            String experienceLevel,
            int weeklyHoursAvailable,
            int estimatedWeeks
    ) {}
}
