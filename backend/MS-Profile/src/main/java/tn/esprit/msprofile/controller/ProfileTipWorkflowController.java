package tn.esprit.msprofile.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.config.StaticUserContext;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.service.ProfileTipService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class ProfileTipWorkflowController {

    private final ProfileTipService profileTipService;
    private final StaticUserContext staticUserContext;

    @GetMapping
    public ResponseEntity<List<ProfileTipResponse>> getTips(@RequestParam(required = false) ProfileType type) {
        if (type != null) {
            return ResponseEntity.ok(profileTipService.getTipsByType(staticUserContext.getCurrentUserId(), type));
        }
        return ResponseEntity.ok(profileTipService.getTipsForUser(staticUserContext.getCurrentUserId()));
    }


    @PatchMapping("/{tipId}/resolve")
    public ResponseEntity<Void> resolveTip(@PathVariable UUID tipId) {
        profileTipService.markTipAsResolved(tipId, staticUserContext.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
