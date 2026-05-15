package tn.esprit.msprofile.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.HireReadinessScoreResponse;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.service.HireReadinessScoreService;

@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
public class ScoreWorkflowController {

    private final HireReadinessScoreService hireReadinessScoreService;
    private final StaticUserContext staticUserContext;

    @GetMapping
    public ResponseEntity<HireReadinessScoreResponse> getScore() {
        try {
            return ResponseEntity.ok(hireReadinessScoreService.getScoreForUser(staticUserContext.getCurrentUserId()));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.ok(hireReadinessScoreService.computeAndSaveScore(staticUserContext.getCurrentUserId()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<HireReadinessScoreResponse> refreshScore() {
        return ResponseEntity.ok(hireReadinessScoreService.refreshScore(staticUserContext.getCurrentUserId()));
    }

}
