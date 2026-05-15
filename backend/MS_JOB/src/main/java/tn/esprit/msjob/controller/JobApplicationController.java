package tn.esprit.msjob.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msjob.dto.JobApplicationDTO;
import tn.esprit.msjob.dto.UpdateApplicationStatusDTO;
import tn.esprit.msjob.service.JobApplicationService;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    public JobApplicationController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    /**
     * Apply to a job with a resume file.
     * Multipart form-data:
     * - jobId (number)
     * - userId (number)  // placeholder until User MS integration
     * - resume (file)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobApplicationDTO> apply(
            @RequestParam @NotNull Long jobId,
            @RequestParam @NotNull Long userId,
            @RequestPart("resume") MultipartFile resume
    ) {
        JobApplicationDTO dto = jobApplicationService.applyToJob(jobId, userId, resume);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    /** Recruiter: list all applications for a specific job. */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<JobApplicationDTO>> listByJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobApplicationService.getApplicationsByJobId(jobId));
    }

    /** Candidate: list all applications for a specific user. */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<JobApplicationDTO>> listByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(jobApplicationService.getApplicationsByUserId(userId));
    }

    /** Update application status (recruiter/admin). */
    @PutMapping("/{applicationId}/status")
    public ResponseEntity<JobApplicationDTO> updateStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody UpdateApplicationStatusDTO body
    ) {
        return ResponseEntity.ok(jobApplicationService.updateStatus(applicationId, body.getStatus()));
    }

    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> delete(@PathVariable Long applicationId) {
        jobApplicationService.deleteApplication(applicationId);
        return ResponseEntity.noContent().build();
    }
}

