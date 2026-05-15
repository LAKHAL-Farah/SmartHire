package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msroadmap.DTO.response.ProgressSummaryDto;
import tn.esprit.msroadmap.DTO.response.RoadmapResponse;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/roadmaps")
@RequiredArgsConstructor
public class AdminRoadmapController {

    private final IRoadmapService roadmapService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<RoadmapResponse> roadmaps = roadmapService.getAllRoadmaps();
        List<ProgressSummaryDto> progress = roadmaps.stream()
                .map(r -> roadmapService.getProgressSummary(r.id()))
                .collect(Collectors.toList());

        long completedRoadmaps = progress.stream()
                .filter(p -> p.getTotalSteps() > 0 && p.getCompletedSteps() >= p.getTotalSteps())
                .count();

        double averageProgress = progress.stream()
                .mapToDouble(ProgressSummaryDto::getProgressPercent)
                .average()
                .orElse(0.0);

        double completionRate = roadmaps.isEmpty() ? 0.0 : (completedRoadmaps * 100.0) / roadmaps.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRoadmaps", roadmaps.size());
        result.put("completedRoadmaps", completedRoadmaps);
        result.put("completionRatePercent", completionRate);
        result.put("averageProgressPercent", averageProgress);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/completion-rates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCompletionRates() {
        List<RoadmapResponse> roadmaps = roadmapService.getAllRoadmaps();

        List<Map<String, Object>> payload = roadmaps.stream().map(r -> {
            ProgressSummaryDto summary = roadmapService.getProgressSummary(r.id());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roadmapId", r.id());
            row.put("title", r.title());
            row.put("completedSteps", summary.getCompletedSteps());
            row.put("totalSteps", summary.getTotalSteps());
            row.put("progressPercent", summary.getProgressPercent());
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/avg-progress")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Double>> getAverageProgress() {
        List<RoadmapResponse> roadmaps = roadmapService.getAllRoadmaps();

        double averageProgress = roadmaps.stream()
                .map(r -> roadmapService.getProgressSummary(r.id()))
                .mapToDouble(ProgressSummaryDto::getProgressPercent)
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(Map.of("averageProgress", averageProgress));
    }
}
