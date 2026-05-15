package tn.esprit.msprofile.service.m4;

import com.fasterxml.jackson.databind.ObjectMapper;
import tn.esprit.msprofile.config.AppConstants;
import tn.esprit.msprofile.dto.m4.CreateJobOfferRequest;
import tn.esprit.msprofile.dto.m4.JobOfferResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ExternalApiException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.mapper.JobOfferMapper;
import tn.esprit.msprofile.repository.CVVersionRepository;
import tn.esprit.msprofile.repository.JobOfferRepository;
import tn.esprit.msprofile.service.AuditLogService;
import tn.esprit.msprofile.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service("m4JobOfferService")
@Slf4j
public class JobOfferService {

    private final JobOfferRepository jobOfferRepository;
    private final OpenAiService openAiService;
    private final AuditLogService auditLogService;
    private final JobOfferMapper jobOfferMapper;
    private final ObjectMapper objectMapper;
    private final CVVersionRepository cvVersionRepository;

    public JobOfferService(
            JobOfferRepository jobOfferRepository,
            OpenAiService openAiService,
            AuditLogService auditLogService,
            JobOfferMapper jobOfferMapper,
            ObjectMapper objectMapper,
            CVVersionRepository cvVersionRepository
    ) {
        this.jobOfferRepository = jobOfferRepository;
        this.openAiService = openAiService;
        this.auditLogService = auditLogService;
        this.jobOfferMapper = jobOfferMapper;
        this.objectMapper = objectMapper;
        this.cvVersionRepository = cvVersionRepository;
    }

    public JobOfferResponse createJobOffer(CreateJobOfferRequest request, String requestedUserId) {
        UUID effectiveUserId = resolveRequestedUserId(requestedUserId);
        JobOffer jobOffer = new JobOffer();
        jobOffer.setUserId(effectiveUserId);
        jobOffer.setTitle(request.title().trim());
        jobOffer.setCompany(trimToNull(request.company()));
        jobOffer.setRawDescription(request.rawDescription().trim());
        jobOffer.setSourceUrl(trimToNull(request.sourceUrl()));
        jobOffer.setCreatedAt(Instant.now());
        jobOffer = jobOfferRepository.save(jobOffer);

        AuditLog keywordLog = auditLogService.logOperation(
            effectiveUserId,
                OperationType.CV_SCORE,
                "JobOffer",
                jobOffer.getId(),
                ProcessingStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        long started = System.nanoTime();
        log.info("Keyword extraction started: jobOfferId={}", jobOffer.getId());
        try {
            OpenAiService.AiResult result = openAiService.extractJobKeywords(jobOffer.getRawDescription());
            jobOffer.setExtractedKeywords(result.content());
            jobOffer = jobOfferRepository.save(jobOffer);

            int keywordsFound = readKeywordCount(result.content());
            auditLogService.updateLog(
                    keywordLog.getId(),
                    ProcessingStatus.COMPLETED,
                    result.tokensUsed(),
                    (int) java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    null
            );
            log.info("Keyword extraction completed: jobOfferId={}, keywordsFound={}, tokensUsed={}",
                    jobOffer.getId(), keywordsFound, result.tokensUsed());
        } catch (Exception ex) {
            auditLogService.updateLog(
                    keywordLog.getId(),
                    ProcessingStatus.FAILED,
                    null,
                    (int) java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    truncate(ex.getMessage(), 500)
            );
            log.error("Keyword extraction failed: jobOfferId={}, error={}", jobOffer.getId(), ex.getMessage());
            // Keep job offer creation available even if AI extraction is temporarily unavailable.
            jobOffer.setExtractedKeywords("[]");
            jobOffer = jobOfferRepository.save(jobOffer);
        }
        return jobOfferMapper.toResponse(jobOffer);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<JobOfferResponse> getJobOffersForUser(String requestedUserId) {
        UUID effectiveUserId = resolveRequestedUserId(requestedUserId);
        return jobOfferMapper.toResponseList(jobOfferRepository.findByUserId(effectiveUserId));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public JobOfferResponse getJobOfferById(String jobOfferId, String requestedUserId) {
        UUID effectiveUserId = resolveRequestedUserId(requestedUserId);
        UUID id = parseUuid(jobOfferId, "jobOfferId");
        JobOffer offer = jobOfferRepository.findByIdAndUserId(id, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Job offer not found"));
        return jobOfferMapper.toResponse(offer);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteJobOffer(String jobOfferId, String requestedUserId) {
        UUID effectiveUserId = resolveRequestedUserId(requestedUserId);
        UUID id = parseUuid(jobOfferId, "jobOfferId");
        JobOffer offer = jobOfferRepository.findByIdAndUserId(id, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Job offer not found"));

        // Detach linked tailored versions first to satisfy FK constraints.
        var linkedVersions = cvVersionRepository.findByJobOfferId(id);
        if (!linkedVersions.isEmpty()) {
            linkedVersions.forEach(version -> version.setJobOffer(null));
            cvVersionRepository.saveAll(linkedVersions);
        }

        jobOfferRepository.delete(offer);
    }

    private UUID resolveRequestedUserId(String requestedUserId) {
        UUID contextUserId = AppConstants.currentUserId();
        if (!AppConstants.DEFAULT_USER_ID.equals(contextUserId)) {
            return contextUserId;
        }

        if (requestedUserId == null || requestedUserId.isBlank()) {
            return contextUserId;
        }

        try {
            return UUID.fromString(requestedUserId.trim());
        } catch (IllegalArgumentException ignored) {
            return contextUserId;
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new tn.esprit.msprofile.exception.ValidationException("Invalid " + fieldName + " UUID value");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int readKeywordCount(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readTree(keywordsJson).size();
        } catch (Exception ignored) {
            // Keyword count is best-effort for logging and should not block API behavior.
            return 0;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
