package tn.esprit.eventmanagement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    @PostMapping("/{userId}")
    public ResponseEntity<?> recommend(@PathVariable Long userId) throws Exception {
        return ResponseEntity.ok(recommendationService.recommend(userId));
    }
}