package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.CertificateDto;
import tn.esprit.msroadmap.Entities.MicroCertificate;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Mapper.CertificateMapper;
import tn.esprit.msroadmap.ServicesImpl.IMicroCertificateService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final IMicroCertificateService microCertificateService;
    private final CertificateMapper certificateMapper;
    private final CurrentUserIdResolver currentUserIdResolver;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CertificateDto>> getUserCertificates(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(resolvedUserId, "userId");
        return ResponseEntity.ok(
            certificateMapper.toDtoList(microCertificateService.getCertificatesByUserId(resolvedUserId))
        );
    }

    @GetMapping("/{certificateCode}")
    public ResponseEntity<CertificateDto> getByCode(@PathVariable String certificateCode) {
        validateCode(certificateCode);
        return ResponseEntity.ok(
                certificateMapper.toDto(microCertificateService.getCertificateByCertificateCode(certificateCode))
        );
    }

    @GetMapping("/{certificateCode}/pdf")
    public ResponseEntity<Void> downloadPdf(@PathVariable String certificateCode) {
        validateCode(certificateCode);
        MicroCertificate certificate = microCertificateService.getCertificateByCertificateCode(certificateCode);
        if (certificate.getPdfUrl() == null || certificate.getPdfUrl().isBlank()) {
            throw new ResourceNotFoundException("PDF not available for this certificate");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(certificate.getPdfUrl()))
                .build();
    }

    @GetMapping("/{certificateCode}/badge")
    public ResponseEntity<Void> downloadBadge(@PathVariable String certificateCode) {
        validateCode(certificateCode);
        MicroCertificate certificate = microCertificateService.getCertificateByCertificateCode(certificateCode);
        if (certificate.getBadgeUrl() == null || certificate.getBadgeUrl().isBlank()) {
            throw new ResourceNotFoundException("Badge not available for this certificate");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(certificate.getBadgeUrl()))
                .build();
    }

    @PostMapping("/generate/{milestoneId}")
    public ResponseEntity<CertificateDto> generateCertificate(
            @PathVariable Long milestoneId,
            @RequestParam(required = false) Long userId
    ) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(milestoneId, "milestoneId");
        validatePositiveId(resolvedUserId, "userId");

        MicroCertificate generated = microCertificateService.generateCertificate(resolvedUserId, milestoneId);
        return ResponseEntity.status(HttpStatus.CREATED).body(certificateMapper.toDto(generated));
    }

    @PostMapping("/{certificateId}/share-linkedin")
    public ResponseEntity<CertificateDto> shareLinkedIn(@PathVariable Long certificateId) {
        validatePositiveId(certificateId, "certificateId");
        return ResponseEntity.ok(
                certificateMapper.toDto(microCertificateService.markLinkedInShared(certificateId))
        );
    }

    private void validatePositiveId(Long id, String field) {
        if (id == null || id <= 0) {
            throw new BusinessException(field + " must be a positive number");
        }
    }

    private void validateCode(String certificateCode) {
        if (certificateCode == null || certificateCode.isBlank()) {
            throw new BusinessException("certificateCode is required");
        }
    }
}
