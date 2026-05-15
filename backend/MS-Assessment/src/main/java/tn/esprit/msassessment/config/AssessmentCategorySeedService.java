package tn.esprit.msassessment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.dto.response.SeedDefaultBankResponse;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;

import java.util.function.Supplier;

/**
 * Seeds {@link AssessmentCategorySeeds} into the DB. Each category is saved in its own transaction so one
 * failure does not roll back previously inserted categories, and errors are logged clearly.
 */
@Slf4j
@Service
public class AssessmentCategorySeedService {

    private final QuestionCategoryRepository categoryRepository;
    private final AssessmentCategorySeedService self;

    public AssessmentCategorySeedService(
            QuestionCategoryRepository categoryRepository,
            @Lazy AssessmentCategorySeedService self) {
        this.categoryRepository = categoryRepository;
        this.self = self;
    }

    /** Inserts every seeded category whose {@code code} is not already present. */
    public int seedAllMissing() {
        int added = 0;
        for (Supplier<QuestionCategory> factory : AssessmentCategorySeeds.allSeedFactories()) {
            if (self.trySeedOne(factory)) {
                added++;
            }
        }
        if (added > 0) {
            log.info("Assessment seed: {} new categor(ies); total categories now {}", added, categoryRepository.count());
        }
        return added;
    }

    /** Same as {@link #seedAllMissing()} but includes current row count (for admin API). */
    public SeedDefaultBankResponse seedAllMissingAndReport() {
        int added = seedAllMissing();
        return new SeedDefaultBankResponse(added, categoryRepository.count());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean trySeedOne(Supplier<QuestionCategory> factory) {
        QuestionCategory candidate = factory.get();
        if (categoryRepository.findByCode(candidate.getCode()).isPresent()) {
            return false;
        }
        try {
            categoryRepository.saveAndFlush(candidate);
            log.info("Seeded assessment category: {} ({})", candidate.getCode(), candidate.getTitle());
            return true;
        } catch (Exception e) {
            log.error("Failed to seed category {}: {}", candidate.getCode(), e.getMessage(), e);
            return false;
        }
    }
}
