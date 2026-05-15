package tn.esprit.msroadmap.Controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.StepRequest;
import tn.esprit.msroadmap.DTO.response.StepResponse;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapStepService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;

@RestController
@RequestMapping("/api/roadmap-steps")
@AllArgsConstructor
public class RoadmapStepController {

    private final IRoadmapStepService stepService;
    private final CurrentUserIdResolver currentUserIdResolver;

    @PostMapping("/{roadmapId}")
    public ResponseEntity<StepResponse> addStep(@PathVariable Long roadmapId, @RequestBody StepRequest request) {
        return ResponseEntity.ok(stepService.addStepToRoadmap(roadmapId, request));
    }

    @GetMapping("/roadmap/{roadmapId}")
    public ResponseEntity<List<StepResponse>> getStepsByRoadmap(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(stepService.getStepsByRoadmapId(roadmapId));
    }

    @DeleteMapping("/{stepId}")
    public ResponseEntity<Void> deleteStep(@PathVariable Long stepId) {
        stepService.deleteStep(stepId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{stepId}/complete")
    public ResponseEntity<Void> completeStep(@PathVariable Long stepId, @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        stepService.completeStep(stepId, resolvedUserId);
        return ResponseEntity.ok().build();
    }
}
