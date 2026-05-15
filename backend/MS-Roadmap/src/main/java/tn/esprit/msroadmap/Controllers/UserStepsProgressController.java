package tn.esprit.msroadmap.Controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.ServicesImpl.IUserStepProgressService;

@RestController
@RequestMapping("/api/user-step-progress")
@AllArgsConstructor
public class UserStepsProgressController {

    private final IUserStepProgressService stepProgressService;

    @PatchMapping("/{userRoadmapId}/steps/{stepId}")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long userRoadmapId,
            @PathVariable Long stepId,
            @RequestParam String status) {
        stepProgressService.updateStepStatus(userRoadmapId, stepId, status);
        return ResponseEntity.noContent().build();
    }
}