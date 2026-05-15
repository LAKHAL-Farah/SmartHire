package tn.esprit.msprofile.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.dto.LinkedInResponse;
import tn.esprit.msprofile.dto.request.AlignLinkedInRequest;
import tn.esprit.msprofile.dto.request.AnalyzeLinkedInRequest;
import tn.esprit.msprofile.service.LinkedInService;

@RestController
@RequestMapping("/api/linkedin")
@RequiredArgsConstructor
public class LinkedInController {

    private final LinkedInService linkedInService;


    @PostMapping("/analyze")
    public ResponseEntity<LinkedInResponse> analyze(@Valid @RequestBody AnalyzeLinkedInRequest request) {
        LinkedInResponse response = linkedInService.analyzeProfile(
                request.rawContent(),
                request.currentHeadline(),
                request.currentSummary(),
                request.currentSkills()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<LinkedInResponse> getProfile() {
        return ResponseEntity.ok(linkedInService.getProfile());
    }

    @PostMapping("/align")
    public ResponseEntity<LinkedInResponse> align(@Valid @RequestBody AlignLinkedInRequest request) {
        return ResponseEntity.ok(linkedInService.alignToJobOffer(request.jobOfferId()));
    }
}
