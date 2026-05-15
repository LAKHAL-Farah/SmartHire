package tn.esprit.msroadmap.Controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.UserRoadmapResponse;
import tn.esprit.msroadmap.ServicesImpl.IUserRoadmapService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

@RestController
@RequestMapping("/api/user-roadmaps")
@AllArgsConstructor
public class UserRoadmapController {

    private final IUserRoadmapService userRoadmapService;
    private final CurrentUserIdResolver currentUserIdResolver;

    @PostMapping("/start")
    public ResponseEntity<UserRoadmapResponse> start(@RequestParam(required = false) Long userId, @RequestParam Long roadmapId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(userRoadmapService.startRoadmap(resolvedUserId, roadmapId));
    }

    @GetMapping("/progress")
    public ResponseEntity<UserRoadmapResponse> getProgress(@RequestParam(required = false) Long userId, @RequestParam Long roadmapId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(userRoadmapService.getUserProgress(resolvedUserId, roadmapId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserRoadmap(@PathVariable Long id) {
        userRoadmapService.deleteUserRoadmap(id);
        return ResponseEntity.noContent().build();
    }
}