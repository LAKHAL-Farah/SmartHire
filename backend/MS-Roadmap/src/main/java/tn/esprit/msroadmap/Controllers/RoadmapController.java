package tn.esprit.msroadmap.Controllers;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.response.PaceSnapshotDto;
import tn.esprit.msroadmap.DTO.request.RoadmapRequest;
import tn.esprit.msroadmap.DTO.response.ProgressSummaryDto;
import tn.esprit.msroadmap.DTO.response.RoadmapResponse;
import tn.esprit.msroadmap.DTO.response.ShareResponseDto;
import tn.esprit.msroadmap.Mapper.PaceSnapshotMapper;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapPaceService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;

@RestController
@RequestMapping("/api/roadmaps")
@RequiredArgsConstructor
public class RoadmapController {

    private final IRoadmapService roadmapService;
    private final IRoadmapPaceService roadmapPaceService;
    private final PaceSnapshotMapper paceSnapshotMapper;
    private final CurrentUserIdResolver currentUserIdResolver;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @PostMapping
    public ResponseEntity<RoadmapResponse> create(@RequestBody RoadmapRequest request) {
        Long userId = currentUserIdResolver.resolveUserId(request.userId());
        RoadmapRequest normalizedRequest = new RoadmapRequest(
            userId,
            request.careerPathId(),
            request.title(),
            request.difficulty(),
            request.estimatedWeeks(),
            request.steps()
        );

        return new ResponseEntity<>(roadmapService.createRoadmap(normalizedRequest), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoadmapResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(roadmapService.getRoadmapById(id));
    }

    @GetMapping
    public ResponseEntity<List<RoadmapResponse>> getAll() {
        return ResponseEntity.ok(roadmapService.getAllRoadmaps());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get latest roadmap for a user (prefers active, falls back to completed)")
    public ResponseEntity<RoadmapResponse> getByUser(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(roadmapService.getRoadmapByUserId(resolvedUserId));
    }

    @GetMapping("/user/{userId}/active")
    @Operation(summary = "Get active roadmap for a user")
    public ResponseEntity<RoadmapResponse> getActiveByUser(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(roadmapService.getActiveRoadmapByUserId(resolvedUserId));
    }

    @GetMapping("/user/{userId}/all")
    @Operation(summary = "Get all roadmaps for a user")
    public ResponseEntity<List<RoadmapResponse>> getAllByUser(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(roadmapService.getRoadmapsByUserId(resolvedUserId));
    }

    @GetMapping("/career-path/{careerPathId}")
    @Operation(summary = "Get roadmaps by career path")
    public ResponseEntity<List<RoadmapResponse>> getByCareerPath(@PathVariable Long careerPathId) {
        return ResponseEntity.ok(roadmapService.getRoadmapsByCareerPath(careerPathId));
    }

    @GetMapping("/{roadmapId}/progress-summary")
    @Operation(summary = "Get progress summary for dashboard")
    public ResponseEntity<ProgressSummaryDto> getProgressSummary(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(roadmapService.getProgressSummary(roadmapId));
    }

    @GetMapping("/{roadmapId}/pace")
    @Operation(summary = "Get latest pace snapshot for a roadmap")
    public ResponseEntity<PaceSnapshotDto> getCurrentPace(@PathVariable Long roadmapId) {
        var latest = roadmapPaceService.getLatestSnapshot(roadmapId);
        if (latest == null) {
            try {
                latest = roadmapPaceService.takeSnapshot(roadmapId);
            } catch (IllegalStateException ignored) {
                latest = roadmapPaceService.getLatestSnapshot(roadmapId);
            }
        }

        PaceSnapshotDto dto = paceSnapshotMapper.toDto(latest);
        if (dto.getCatchUpPlanNote() == null || dto.getCatchUpPlanNote().isBlank()) {
            dto.setCatchUpPlanNote(roadmapPaceService.computeCatchUpPlan(roadmapId));
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate roadmap")
    public ResponseEntity<RoadmapResponse> generate(@RequestBody RoadmapGenerationRequestDto request) {
        Long userId = currentUserIdResolver.resolveUserId(request.getUserId());
        request.setUserId(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(roadmapService.generateRoadmap(request));
    }

    @PutMapping("/{id}/pause")
    @Operation(summary = "Pause a roadmap")
    public ResponseEntity<Void> pause(@PathVariable Long id) {
        roadmapService.pauseRoadmap(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/resume")
    @Operation(summary = "Resume a roadmap")
    public ResponseEntity<Void> resume(@PathVariable Long id) {
        roadmapService.resumeRoadmap(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Create share token for roadmap")
    public ResponseEntity<ShareResponseDto> share(@PathVariable Long id) {
        String token = roadmapService.shareRoadmap(id);
        return ResponseEntity.ok(ShareResponseDto.builder()
                .shareToken(token)
            .shareUrl(frontendBaseUrl + "/roadmaps/public/" + token)
                .isPublic(true)
                .build());
    }

    @DeleteMapping("/{id}/share")
    @Operation(summary = "Disable public sharing for roadmap")
    public ResponseEntity<Void> unshare(@PathVariable Long id) {
        roadmapService.unshareRoadmap(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/{token}")
    @Operation(summary = "Get public roadmap by token")
    public ResponseEntity<RoadmapResponse> getByShareToken(@PathVariable String token) {
        return ResponseEntity.ok(roadmapService.getRoadmapByShareToken(token));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoadmapResponse> update(@PathVariable Long id, @RequestBody RoadmapRequest request) {
        return ResponseEntity.ok(roadmapService.updateRoadmap(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roadmapService.deleteRoadmap(id);
        return ResponseEntity.noContent().build();
    }
}