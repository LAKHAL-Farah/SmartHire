package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.ProjectSuggestion;
import tn.esprit.msroadmap.Enums.DifficultyLevel;

import java.util.List;

@Repository
public interface ProjectSuggestionRepository extends JpaRepository<ProjectSuggestion, Long> {
    List<ProjectSuggestion> findByStepId(Long stepId);
    List<ProjectSuggestion> findByStepIdOrderByCreatedAtDescIdDesc(Long stepId);
    List<ProjectSuggestion> findByStepIdAndDifficulty(Long stepId, DifficultyLevel difficulty);
    List<ProjectSuggestion> findByDifficulty(DifficultyLevel difficulty);
    List<ProjectSuggestion> findByStepRoadmapIdAndStepStepOrderOrderByCreatedAtDescIdDesc(Long roadmapId, Integer stepOrder);
}
