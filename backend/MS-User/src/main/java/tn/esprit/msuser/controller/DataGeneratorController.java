package tn.esprit.msuser.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msuser.entity.enumerated.RoleName;
import tn.esprit.msuser.service.DataGeneratorService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-generator")
@RequiredArgsConstructor
public class DataGeneratorController {

    private final DataGeneratorService dataGeneratorService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(dataGeneratorService.getStatistics());
    }

    @PostMapping("/generate-startup-team")
    public ResponseEntity<String> generateStartupTeam() {
        dataGeneratorService.generateStartupTeam();
        return ResponseEntity.ok("Équipe startup générée avec succès!");
    }

    @PostMapping("/generate-user")
    public ResponseEntity<String> generateSpecificUser(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam String headline,
            @RequestParam String city,
            @RequestParam String roleName) {

        try {
            RoleName role = RoleName.valueOf(roleName.toUpperCase());
            dataGeneratorService.generateUserWithSpecificProfile(
                    firstName, lastName, email, headline, city, role);
            return ResponseEntity.ok("Utilisateur généré avec succès!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Rôle invalide: " + roleName);
        }
    }
}