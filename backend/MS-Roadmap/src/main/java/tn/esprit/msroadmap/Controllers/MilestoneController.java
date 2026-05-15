package tn.esprit.msroadmap.Controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.CreateMilestoneRequestDto;
import tn.esprit.msroadmap.DTO.response.MilestoneDto;
import tn.esprit.msroadmap.Entities.RoadmapMilestone;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Mapper.MilestoneMapper;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapMilestoneService;

import java.util.List;

@RestController
@RequestMapping("/api/milestones")
@RequiredArgsConstructor
@Tag(name = "Milestones", description = "Roadmap milestone management")
public class MilestoneController {

    private final IRoadmapMilestoneService milestoneService;
    private final MilestoneMapper milestoneMapper;

    @GetMapping("/roadmap/{roadmapId}")
    @Operation(summary = "Get all milestones for a roadmap")
    public ResponseEntity<List<MilestoneDto>> getMilestonesByRoadmap(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(milestoneMapper.toDtoList(
                milestoneService.getMilestonesByRoadmapId(roadmapId)));
    }

    @GetMapping("/roadmap/{roadmapId}/next")
    @Operation(summary = "Get next upcoming milestone")
    public ResponseEntity<MilestoneDto> getNextMilestone(@PathVariable Long roadmapId) {
        List<RoadmapMilestone> milestones = milestoneService.getMilestonesByRoadmapId(roadmapId);
        RoadmapMilestone next = milestones.stream()
                .filter(m -> m.getReachedAt() == null)
                .findFirst()
                .orElse(null);
        return ResponseEntity.ok(milestoneMapper.toDto(next));
    }

    @PostMapping("/roadmap/{roadmapId}")
    @Operation(summary = "Create a new milestone")
    public ResponseEntity<MilestoneDto> createMilestone(
            @PathVariable Long roadmapId,
            @RequestBody CreateMilestoneRequestDto request) {
        if (request.getStepThreshold() <= 0) {
            throw new BusinessException("stepThreshold must be > 0");
        }
        RoadmapMilestone milestone = new RoadmapMilestone();
        milestone.setTitle(request.getTitle());
        milestone.setDescription(request.getDescription());
        milestone.setStepThreshold(request.getStepThreshold());
        RoadmapMilestone created = milestoneService.createMilestone(roadmapId, milestone);
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneMapper.toDto(created));
    }
}
