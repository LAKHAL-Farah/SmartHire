package tn.esprit.msprofile.controller.m4;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msprofile.dto.m4.AtsScoreResponse;
import tn.esprit.msprofile.dto.m4.CandidateCvResponse;
import tn.esprit.msprofile.dto.m4.CvVersionResponse;
import tn.esprit.msprofile.dto.m4.TailorCvRequest;
import tn.esprit.msprofile.service.m4.CandidateCvService;

import java.util.List;

/**
 * CORS is handled by the API Gateway.
 */
@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
public class CandidateCvController {

    private final CandidateCvService cvService;

    @PostMapping("/upload")
    public ResponseEntity<CandidateCvResponse> uploadCv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cvService.uploadAndParseCv(file, userId));
    }

    @GetMapping
    public ResponseEntity<List<CandidateCvResponse>> getAll() {
        return ResponseEntity.ok(cvService.getAllCvs());
    }

    @GetMapping("/{cvId}")
    public ResponseEntity<CandidateCvResponse> getById(@PathVariable String cvId) {
        return ResponseEntity.ok(cvService.getCvById(cvId));
    }


    @GetMapping("/{cvId}/score")
    public ResponseEntity<AtsScoreResponse> getScore(@PathVariable String cvId) {
        return ResponseEntity.ok(cvService.getCvScore(cvId));
    }

    @PostMapping("/{cvId}/tailor")
    public ResponseEntity<CvVersionResponse> tailorCv(@PathVariable String cvId, @RequestBody @Valid TailorCvRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cvService.tailorCvForJobOffer(cvId, request.jobOfferId()));
    }


    @PostMapping("/{cvId}/optimize")
    public ResponseEntity<CvVersionResponse> optimizeCv(@PathVariable String cvId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cvService.optimizeCvGenerically(cvId));
    }

    @GetMapping("/{cvId}/versions")
    public ResponseEntity<List<CvVersionResponse>> getVersions(@PathVariable String cvId) {
        return ResponseEntity.ok(cvService.getVersionsForCv(cvId));
    }

    @GetMapping("/versions/{versionId}")
    public ResponseEntity<CvVersionResponse> getVersionById(@PathVariable String versionId) {
        return ResponseEntity.ok(cvService.getVersionById(versionId));
    }

    @GetMapping("/versions/{versionId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String versionId) {
        byte[] bytes = cvService.exportVersionAsPdf(versionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cv-optimized.pdf\"")
                .body(bytes);
    }

    @DeleteMapping("/{cvId}")
    public ResponseEntity<Void> deactivate(@PathVariable String cvId) {
        cvService.deactivateCv(cvId);
        return ResponseEntity.noContent().build();
    }
}
