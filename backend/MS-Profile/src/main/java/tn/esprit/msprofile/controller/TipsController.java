package tn.esprit.msprofile.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.config.AppConstants;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.service.ProfileTipService;

import java.util.List;
import java.util.UUID;

/**
 * CORS is handled by the API Gateway.
 */
@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
public class TipsController {

    private final ProfileTipService profileTipService;

    @GetMapping
    public ResponseEntity<List<ProfileTipResponse>> getTips(@RequestParam(value = "type", required = false) String type) {
        if (type == null || type.isBlank()) {
            return ResponseEntity.ok(profileTipService.getTipsForUser(AppConstants.currentUserId()));
        }
        return ResponseEntity.ok(profileTipService.getTipsByType(AppConstants.currentUserId(), ProfileType.valueOf(type)));
    }

    @PatchMapping("/{tipId}/resolve")
    public ResponseEntity<Void> resolveTip(@PathVariable String tipId) {
        profileTipService.markTipAsResolved(UUID.fromString(tipId), AppConstants.currentUserId());
        return ResponseEntity.ok().build();
    }
}
