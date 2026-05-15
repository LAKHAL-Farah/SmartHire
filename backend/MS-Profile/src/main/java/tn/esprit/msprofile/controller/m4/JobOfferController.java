package tn.esprit.msprofile.controller.m4;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msprofile.dto.m4.CreateJobOfferRequest;
import tn.esprit.msprofile.dto.m4.JobOfferResponse;
import tn.esprit.msprofile.service.m4.JobOfferService;

import java.util.List;

/**
 * CORS is handled by the API Gateway.
 */
@RestController("m4JobOfferController")
@RequestMapping("/api/job-offers")
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService jobOfferService;

    @PostMapping
    public ResponseEntity<JobOfferResponse> create(
            @RequestBody @Valid CreateJobOfferRequest request,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobOfferService.createJobOffer(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<JobOfferResponse>> getAll(
            @RequestParam(value = "userId", required = false) String userId
    ) {
        return ResponseEntity.ok(jobOfferService.getJobOffersForUser(userId));
    }

    @GetMapping("/{jobOfferId}")
    public ResponseEntity<JobOfferResponse> getById(
            @PathVariable String jobOfferId,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        return ResponseEntity.ok(jobOfferService.getJobOfferById(jobOfferId, userId));
    }

    @DeleteMapping("/{jobOfferId}")
    public ResponseEntity<Void> delete(
            @PathVariable String jobOfferId,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        jobOfferService.deleteJobOffer(jobOfferId, userId);
        return ResponseEntity.noContent().build();
    }
}
