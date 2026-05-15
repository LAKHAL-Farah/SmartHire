package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.RoadmapResponse;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/roadmaps/public")
@RequiredArgsConstructor
public class PublicRoadmapController {

    private final IRoadmapService roadmapService;

    @GetMapping("/{shareToken}/embed")
    public ResponseEntity<Map<String, Object>> viewPublicRoadmapEmbed(@PathVariable String shareToken) {
        validateToken(shareToken);
        RoadmapResponse roadmap = roadmapService.getRoadmapByShareToken(shareToken);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("embed", true);
        payload.put("roadmap", roadmap);

        return ResponseEntity.ok(payload);
    }

    private void validateToken(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) {
            throw new BusinessException("shareToken is required");
        }
    }
}
