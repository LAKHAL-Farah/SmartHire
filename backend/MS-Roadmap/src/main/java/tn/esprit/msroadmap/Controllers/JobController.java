package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.JobDto;
import tn.esprit.msroadmap.DTO.response.SavedJobDto;
import tn.esprit.msroadmap.Services.JobService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final CurrentUserIdResolver currentUserIdResolver;

    @GetMapping("/search")
    public ResponseEntity<Page<JobDto>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(jobService.searchJobs(keyword, location, page, size));
    }

    @GetMapping("/recommendations/{userId}")
    public ResponseEntity<List<JobDto>> getRecommendations(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(jobService.getRecommendations(resolvedUserId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<Void> saveJob(@PathVariable String id, @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        jobService.saveJobForUser(resolvedUserId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/saved/{userId}")
    public ResponseEntity<List<SavedJobDto>> getSavedJobs(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(jobService.getSavedJobs(resolvedUserId));
    }

    @DeleteMapping("/saved/{savedJobId}")
    public ResponseEntity<Void> removeSavedJob(@PathVariable Long savedJobId) {
        jobService.removeSavedJob(savedJobId);
        return ResponseEntity.noContent().build();
    }
}
