package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.ProjectSuggestionDto;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Mapper.ProjectSuggestionMapper;
import tn.esprit.msroadmap.ServicesImpl.IProjectSuggestionService;

import java.util.List;

@RestController
@RequestMapping("/api/project-suggestions")
@RequiredArgsConstructor
public class ProjectSuggestionController {

    private final IProjectSuggestionService projectSuggestionService;
    private final ProjectSuggestionMapper projectSuggestionMapper;

    @PostMapping("/generate/{stepId}")
    public ResponseEntity<List<ProjectSuggestionDto>> generate(
            @PathVariable Long stepId,
            @RequestParam String domain,
            @RequestParam String level) {
        if (stepId == null || stepId <= 0) {
            throw new BusinessException("stepId must be a positive number");
        }
        if (domain == null || domain.isBlank()) {
            throw new BusinessException("domain is required");
        }
        if (level == null || level.isBlank()) {
            throw new BusinessException("level is required");
        }

        return ResponseEntity.ok(projectSuggestionMapper.toDtoList(
                projectSuggestionService.generateProjectSuggestions(stepId, domain, level)));
    }

    @PostMapping("/generate/roadmap/{roadmapId}/step/{stepOrder}")
    public ResponseEntity<List<ProjectSuggestionDto>> generateForRoadmapStep(
            @PathVariable Long roadmapId,
            @PathVariable Integer stepOrder,
            @RequestParam String domain,
            @RequestParam String level) {
        if (roadmapId == null || roadmapId <= 0) {
            throw new BusinessException("roadmapId must be a positive number");
        }
        if (stepOrder == null || stepOrder <= 0) {
            throw new BusinessException("stepOrder must be a positive number");
        }
        if (domain == null || domain.isBlank()) {
            throw new BusinessException("domain is required");
        }
        if (level == null || level.isBlank()) {
            throw new BusinessException("level is required");
        }

        return ResponseEntity.ok(projectSuggestionMapper.toDtoList(
                projectSuggestionService.generateProjectSuggestionsByRoadmapStep(roadmapId, stepOrder, domain, level)));
    }

    @GetMapping("/roadmap/{roadmapId}/step/{stepOrder}")
    public ResponseEntity<List<ProjectSuggestionDto>> getByRoadmapStep(
            @PathVariable Long roadmapId,
            @PathVariable Integer stepOrder) {
        if (roadmapId == null || roadmapId <= 0) {
            throw new BusinessException("roadmapId must be a positive number");
        }
        if (stepOrder == null || stepOrder <= 0) {
            throw new BusinessException("stepOrder must be a positive number");
        }

        return ResponseEntity.ok(projectSuggestionMapper.toDtoList(
                projectSuggestionService.getSuggestionsByRoadmapStep(roadmapId, stepOrder)));
    }
}
