package tn.esprit.msroadmap.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tn.esprit.msroadmap.DTO.request.RoadmapRequest;
import tn.esprit.msroadmap.DTO.request.StepRequest;
import tn.esprit.msroadmap.Enums.RoadmapStatus;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.test-user.enabled", havingValue = "true")
public class TestUserSeedConfig implements ApplicationRunner {

    private static final long TEST_USER_ID = 1L;

    private final RoadmapRepository roadmapRepository;
    private final IRoadmapService roadmapService;

    @Override
    public void run(ApplicationArguments args) {
        boolean hasActiveRoadmap = !roadmapRepository.findByUserIdAndStatus(TEST_USER_ID, RoadmapStatus.ACTIVE).isEmpty();
        if (hasActiveRoadmap) {
            return;
        }

        RoadmapRequest seedRoadmap = new RoadmapRequest(
            TEST_USER_ID,
            10L,
            "Backend Java Test Roadmap",
            "INTERMEDIATE",
            8,
            List.of(
                new StepRequest("Learn Spring Boot Basics", 1, 5),
                new StepRequest("Build REST APIs", 2, 6),
                new StepRequest("Database Integration (JPA)", 3, 6)
            )
        );

        roadmapService.createRoadmap(seedRoadmap);
        log.info("Seeded test roadmap for userId={}", TEST_USER_ID);
    }
}