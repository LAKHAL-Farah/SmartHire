package tn.esprit.msassessment.controller;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msassessment.dto.response.SkillProfileResponse;
import tn.esprit.msassessment.service.SkillProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/skill-profiles")
@RequiredArgsConstructor
public class SkillProfileController {

    private final SkillProfileService skillProfileService;

    /** Latest aggregated profile for dashboards (MS-Roadmap, matching, candidate profile). */
    @GetMapping("/user/{userId}")
    public ResponseEntity<SkillProfileResponse> getForUser(@PathVariable @NotNull UUID userId) {
        return ResponseEntity.ok(skillProfileService.getForUser(userId));
    }
}
