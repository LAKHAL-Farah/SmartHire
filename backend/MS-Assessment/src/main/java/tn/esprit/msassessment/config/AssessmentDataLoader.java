package tn.esprit.msassessment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the question bank seed on startup (see {@link AssessmentCategorySeedService}).
 */
@Component
@RequiredArgsConstructor
public class AssessmentDataLoader implements ApplicationRunner {

    private final AssessmentCategorySeedService seedService;

    @Override
    public void run(ApplicationArguments args) {
        seedService.seedAllMissing();
    }
}
