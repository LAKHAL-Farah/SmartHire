package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.ProjectSuggestion;
import tn.esprit.msroadmap.Enums.DifficultyLevel;

import java.util.List;

public interface IProjectSuggestionService {
    List<ProjectSuggestion> getSuggestionsByStepId(Long stepId);
    List<ProjectSuggestion> getSuggestionsByDifficulty(DifficultyLevel difficulty);
    List<ProjectSuggestion> browseSuggestions(DifficultyLevel difficulty, String tech, int page, int size);
    List<ProjectSuggestion> generateProjectSuggestions(Long stepId, String domain, String level);
    List<ProjectSuggestion> generateProjectSuggestionsByRoadmapStep(Long roadmapId, Integer stepOrder, String domain, String level);
    List<ProjectSuggestion> getSuggestionsByRoadmapStep(Long roadmapId, Integer stepOrder);
}
