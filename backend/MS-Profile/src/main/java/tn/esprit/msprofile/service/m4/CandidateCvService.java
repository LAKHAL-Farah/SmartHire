package tn.esprit.msprofile.service.m4;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import tn.esprit.msprofile.config.AppConstants;
import tn.esprit.msprofile.dto.CompletenessResult;
import tn.esprit.msprofile.dto.DiffResult;
import tn.esprit.msprofile.dto.m4.AtsScoreResponse;
import tn.esprit.msprofile.dto.m4.CandidateCvResponse;
import tn.esprit.msprofile.dto.m4.CvVersionResponse;
import tn.esprit.msprofile.entity.AuditLog;
import tn.esprit.msprofile.entity.CVVersion;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ExternalApiException;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.exception.ValidationException;
import tn.esprit.msprofile.mapper.CandidateCvMapper;
import tn.esprit.msprofile.mapper.CvVersionMapper;
import tn.esprit.msprofile.repository.CVVersionRepository;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.JobOfferRepository;
import tn.esprit.msprofile.service.AtsScoreService;
import tn.esprit.msprofile.service.AtsAnalysisResult;
import tn.esprit.msprofile.service.AuditLogService;
import tn.esprit.msprofile.service.CompletenessService;
import tn.esprit.msprofile.service.DiffService;
import tn.esprit.msprofile.service.FileStorageService;
import tn.esprit.msprofile.service.OpenAiService;
import tn.esprit.msprofile.service.PdfExportService;
import tn.esprit.msprofile.service.ProfileTipService;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CandidateCvService {

    private static final String BASELINE_JOB_DESCRIPTION =
        "Software engineering role requiring technical skills, professional experience, " +
            "clear communication, and relevant education";

    private final CandidateCVRepository candidateCVRepository;
    private final CVVersionRepository cvVersionRepository;
    private final JobOfferRepository jobOfferRepository;
    private final FileStorageService fileStorageService;
    private final OpenAiService openAiService;
    private final AtsScoreService atsScoreService;
    private final PdfExportService pdfExportService;
    private final AuditLogService auditLogService;
    private final DiffService diffService;
    private final CompletenessService completenessService;
    private final ProfileTipService profileTipService;
    private final CandidateCvMapper candidateCvMapper;
    private final CvVersionMapper cvVersionMapper;
    private final ObjectMapper objectMapper;

    public CandidateCvService(
            CandidateCVRepository candidateCVRepository,
            CVVersionRepository cvVersionRepository,
            JobOfferRepository jobOfferRepository,
            FileStorageService fileStorageService,
            OpenAiService openAiService,
            AtsScoreService atsScoreService,
            PdfExportService pdfExportService,
            AuditLogService auditLogService,
            DiffService diffService,
            CompletenessService completenessService,
            ProfileTipService profileTipService,
            CandidateCvMapper candidateCvMapper,
            CvVersionMapper cvVersionMapper,
            ObjectMapper objectMapper
    ) {
        this.candidateCVRepository = candidateCVRepository;
        this.cvVersionRepository = cvVersionRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.fileStorageService = fileStorageService;
        this.openAiService = openAiService;
        this.atsScoreService = atsScoreService;
        this.pdfExportService = pdfExportService;
        this.auditLogService = auditLogService;
        this.diffService = diffService;
        this.completenessService = completenessService;
        this.profileTipService = profileTipService;
        this.candidateCvMapper = candidateCvMapper;
        this.cvVersionMapper = cvVersionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CandidateCvResponse uploadAndParseCv(MultipartFile file, String requestedUserId) {
        UUID effectiveUserId = resolveUploadUserId(requestedUserId);

        deactivateExistingActiveCv(effectiveUserId);
        String filePath = fileStorageService.store(file, effectiveUserId);
        FileFormat fileFormat = resolveFileFormat(file.getOriginalFilename());

        CandidateCV cv = new CandidateCV();
        cv.setUserId(effectiveUserId);
        cv.setOriginalFileUrl(filePath);
        cv.setOriginalFileName(safeFileName(file.getOriginalFilename()));
        cv.setFileFormat(fileFormat);
        cv.setParseStatus(ProcessingStatus.IN_PROGRESS);
        cv.setIsActive(true);
        cv.setUploadedAt(Instant.now());
        cv.setUpdatedAt(Instant.now());
        cv = candidateCVRepository.save(cv);

        AuditLog parseLog = auditLogService.logOperation(
            effectiveUserId,
                OperationType.CV_PARSE,
                "CandidateCV",
                cv.getId(),
                ProcessingStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        long started = System.nanoTime();
        log.info("CV parsing started: cvId={}, fileName={}", cv.getId(), cv.getOriginalFileName());
        try {
            String rawText = extractRawText(filePath);
            OpenAiService.AiResult parseResult = openAiService.extractStructuredCvContent(rawText);
            String normalizedParsedContent = normalizeCvJson(parseResult.content(), null);

            cv.setParsedContent(normalizedParsedContent);
            cv.setParseStatus(ProcessingStatus.COMPLETED);
            AtsAnalysisResult analysis = atsScoreService.computeFullAnalysis(
                normalizedParsedContent,
                BASELINE_JOB_DESCRIPTION,
                List.of()
            );
            cv.setAtsScore(analysis.overallScore());
            cv.setAtsAnalysis(analysis.rawJson());
            cv.setCompletenessAnalysis(computeCompletenessJson(parseResult.content()));
            cv.setUpdatedAt(Instant.now());
            cv = candidateCVRepository.save(cv);

            try {
                profileTipService.generateTipsForCv(cv.getUserId(), cv.getId());
            } catch (Exception tipException) {
                log.warn("Profile tip generation failed after CV parse: cvId={}, error={}", cv.getId(), tipException.getMessage());
            }

            int durationMs = (int) Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("CV parsing completed: cvId={}, tokensUsed={}, durationMs={}, score={}",
                cv.getId(), parseResult.tokensUsed(), durationMs, analysis.overallScore());

            auditLogService.updateLog(
                    parseLog.getId(),
                    ProcessingStatus.COMPLETED,
                parseResult.tokensUsed(),
                durationMs,
                    null
            );
            return candidateCvMapper.toResponse(cv);
        } catch (Exception e) {
            cv.setParseStatus(ProcessingStatus.FAILED);
            cv.setParseErrorMessage(truncate(e.getMessage(), 500));
            cv.setUpdatedAt(Instant.now());
            candidateCVRepository.save(cv);
            log.error("CV parsing failed: cvId={}, error={}", cv.getId(), e.getMessage());
            auditLogService.updateLog(
                    parseLog.getId(),
                    ProcessingStatus.FAILED,
                    null,
                    (int) Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    truncate(e.getMessage(), 500)
            );
            if (e instanceof ExternalApiException runtime) {
                throw runtime;
            }
            throw new ValidationException("Failed to parse uploaded CV");
        }
    }

    private UUID resolveUploadUserId(String requestedUserId) {
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

    @Transactional(readOnly = true)
    public List<CandidateCvResponse> getAllCvs() {
        return candidateCVRepository.findByUserIdAndIsActiveTrue(AppConstants.currentUserId())
                .stream()
                .map(candidateCvMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateCvResponse getCvById(String cvId) {
        UUID id = parseUuid(cvId, "cvId");
        CandidateCV cv = candidateCVRepository.findByIdAndUserId(id, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));
        return candidateCvMapper.toResponse(cv);
    }

    @Transactional
    public CandidateCvResponse setActiveCv(String cvId) {
        UUID parsedCvId = parseUuid(cvId, "cvId");

        CandidateCV cv = candidateCVRepository.findByIdAndUserId(parsedCvId, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));

        deactivateExistingActiveCv(AppConstants.currentUserId());
        cv.setIsActive(Boolean.TRUE);
        cv = candidateCVRepository.save(cv);

        return candidateCvMapper.toResponse(cv);
    }

    @Transactional(readOnly = true)
    public AtsScoreResponse getCvScore(String cvId) {
        UUID id = parseUuid(cvId, "cvId");
        CandidateCV cv = candidateCVRepository.findByIdAndUserId(id, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));
        return new AtsScoreResponse(cv.getId(), cv.getAtsScore());
    }

    @Transactional
    public CvVersionResponse tailorCvForJobOffer(String cvId, String jobOfferId) {
        UUID parsedCvId = parseUuid(cvId, "cvId");
        UUID parsedJobOfferId = parseUuid(jobOfferId, "jobOfferId");

        CandidateCV cv = candidateCVRepository.findByIdAndUserId(parsedCvId, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));
        if (cv.getParseStatus() != ProcessingStatus.COMPLETED) {
            throw new ValidationException("CV must be parsed successfully before tailoring");
        }

        JobOffer jobOffer = jobOfferRepository.findByIdAndUserId(parsedJobOfferId, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Job offer not found"));

        List<String> keywords = readKeywords(jobOffer.getExtractedKeywords());
        if (keywords.isEmpty()) {
            throw new ValidationException("Job offer keywords not extracted yet — please wait and retry");
        }

        AuditLog tailorLog = auditLogService.logOperation(
                AppConstants.currentUserId(),
                OperationType.CV_TAILOR,
                "CandidateCV",
                cv.getId(),
                ProcessingStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        long started = System.nanoTime();
        log.info("CV tailoring started: cvId={}, jobOfferId={}", cv.getId(), jobOffer.getId());
        try {
            String baselineContent = normalizeCvJson(cv.getParsedContent(), cv.getParsedContent());
            CandidateEvaluation baseline = evaluateCandidate(
                    "baseline",
                    cv.getParsedContent(),
                    baselineContent,
                    jobOffer.getRawDescription(),
                    keywords,
                    0
            );

            List<CandidateEvaluation> candidates = new java.util.ArrayList<>();
            int consumedTokens = baseline.tokensUsed();

            try {
                OpenAiService.AiResult primaryResult = openAiService.tailorCvContent(
                        cv.getParsedContent(),
                        jobOffer.getRawDescription(),
                        keywords
                );
                String normalizedPrimary = normalizeCvJson(primaryResult.content(), cv.getParsedContent());
                CandidateEvaluation primary = evaluateCandidate(
                        "tailor-primary",
                        cv.getParsedContent(),
                        normalizedPrimary,
                        jobOffer.getRawDescription(),
                        keywords,
                        primaryResult.tokensUsed()
                );
                candidates.add(primary);
                consumedTokens += primary.tokensUsed();
            } catch (ExternalApiException ex) {
                log.warn("Tailor primary attempt unavailable: cvId={}, jobOfferId={}, error={}",
                        cv.getId(), jobOffer.getId(), ex.getMessage());
            }

            if (candidates.isEmpty() || isRegression(candidates.get(0), baseline)) {
                try {
                    OpenAiService.AiResult retryResult = openAiService.tailorCvContent(
                            cv.getParsedContent(),
                            buildTailorRetryDescription(jobOffer.getRawDescription()),
                            keywords
                    );
                    String normalizedRetry = normalizeCvJson(retryResult.content(), cv.getParsedContent());
                    CandidateEvaluation retry = evaluateCandidate(
                            "tailor-retry",
                            cv.getParsedContent(),
                            normalizedRetry,
                            jobOffer.getRawDescription(),
                            keywords,
                            retryResult.tokensUsed()
                    );
                    candidates.add(retry);
                    consumedTokens += retry.tokensUsed();
                } catch (ExternalApiException ex) {
                    log.warn("Tailor retry attempt unavailable: cvId={}, jobOfferId={}, error={}",
                            cv.getId(), jobOffer.getId(), ex.getMessage());
                }
            }

            CandidateEvaluation selected = selectBestCandidate(baseline, candidates);
            if ("baseline".equals(selected.label())) {
                log.warn("Tailor attempts were regressive; preserving baseline CV for safety: cvId={}, jobOfferId={}",
                        cv.getId(), jobOffer.getId());
            }

            CVVersion version = new CVVersion();
            version.setCv(cv);
            version.setJobOffer(jobOffer);
            version.setVersionType(CVVersionType.TAILORED);
            version.setTailoredContent(selected.content());
            version.setAtsScore(selected.analysis().overallScore());
            version.setKeywordMatchRate(selected.keywordMatchRate());
            version.setAtsAnalysis(selected.analysis().rawJson());
            version.setDiffContent(diffService.serializeCompactSnapshot(selected.diff()));
            version.setCompletenessAnalysis(selected.completenessJson());
            version.setGeneratedByAI(!"baseline".equals(selected.label()));
            version.setProcessingStatus(ProcessingStatus.COMPLETED);
            version = cvVersionRepository.save(version);

            if (cv.getAtsScore() == null || selected.analysis().overallScore() > cv.getAtsScore()) {
                cv.setAtsScore(selected.analysis().overallScore());
                candidateCVRepository.save(cv);
            }

            int durationMs = (int) Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("CV tailoring completed: cvId={}, versionId={}, newScore={}, tokensUsed={}",
                    cv.getId(), version.getId(), selected.analysis().overallScore(), consumedTokens);

            try {
                profileTipService.generateTipsForCv(cv.getUserId(), cv.getId());
            } catch (Exception tipException) {
                log.warn("Profile tip generation failed after tailoring: cvId={}, error={}", cv.getId(), tipException.getMessage());
            }

            auditLogService.updateLog(
                    tailorLog.getId(),
                    ProcessingStatus.COMPLETED,
                    consumedTokens,
                    durationMs,
                    null
            );
            return cvVersionMapper.toResponse(version);
        } catch (Exception e) {
            log.error("CV tailoring failed: cvId={}, jobOfferId={}, error={}", cv.getId(), jobOffer.getId(), e.getMessage());
            auditLogService.updateLog(
                    tailorLog.getId(),
                    ProcessingStatus.FAILED,
                    null,
                    (int) Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    truncate(e.getMessage(), 500)
            );
            CVVersion failedVersion = new CVVersion();
            failedVersion.setCv(cv);
            failedVersion.setJobOffer(jobOffer);
            failedVersion.setVersionType(CVVersionType.TAILORED);
            failedVersion.setTailoredContent(cv.getParsedContent());
            failedVersion.setGeneratedByAI(true);
            failedVersion.setProcessingStatus(ProcessingStatus.FAILED);
            cvVersionRepository.save(failedVersion);
            if (e instanceof ExternalApiException runtime) {
                throw runtime;
            }
            throw new ExternalApiException("AI service temporarily unavailable", e);
        }
    }

    @Transactional
    public CvVersionResponse optimizeCvGenerically(String cvId) {
        UUID parsedCvId = parseUuid(cvId, "cvId");

        CandidateCV cv = candidateCVRepository.findByIdAndUserId(parsedCvId, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));
        if (cv.getParseStatus() != ProcessingStatus.COMPLETED) {
            throw new ValidationException("CV must be parsed successfully before optimization");
        }

        AuditLog optimizeLog = auditLogService.logOperation(
                AppConstants.currentUserId(),
                OperationType.CV_TAILOR,
                "CandidateCV",
                cv.getId(),
                ProcessingStatus.IN_PROGRESS,
                null,
                null,
                null
        );

        long started = System.nanoTime();
        log.info("CV generic optimization started: cvId={}", cv.getId());
        try {
            String baselineContent = normalizeCvJson(cv.getParsedContent(), cv.getParsedContent());
            CandidateEvaluation baseline = evaluateCandidate(
                    "baseline",
                    cv.getParsedContent(),
                    baselineContent,
                    BASELINE_JOB_DESCRIPTION,
                    List.of(),
                    0
            );

            List<CandidateEvaluation> candidates = new java.util.ArrayList<>();
            int consumedTokens = baseline.tokensUsed();

            try {
                OpenAiService.AiResult primaryResult = openAiService.optimizeCvGenerically(cv.getParsedContent());
                String normalizedPrimary = normalizeCvJson(primaryResult.content(), cv.getParsedContent());
                CandidateEvaluation primary = evaluateCandidate(
                        "optimize-primary",
                        cv.getParsedContent(),
                        normalizedPrimary,
                        BASELINE_JOB_DESCRIPTION,
                        List.of(),
                        primaryResult.tokensUsed()
                );
                candidates.add(primary);
                consumedTokens += primary.tokensUsed();
            } catch (ExternalApiException ex) {
                log.warn("Generic optimize primary attempt unavailable: cvId={}, error={}", cv.getId(), ex.getMessage());
            }

            if (candidates.isEmpty() || isRegression(candidates.get(0), baseline)) {
                try {
                    OpenAiService.AiResult retryResult = openAiService.optimizeCvGenerically(cv.getParsedContent());
                    String normalizedRetry = normalizeCvJson(retryResult.content(), cv.getParsedContent());
                    CandidateEvaluation retry = evaluateCandidate(
                            "optimize-retry",
                            cv.getParsedContent(),
                            normalizedRetry,
                            BASELINE_JOB_DESCRIPTION,
                            List.of(),
                            retryResult.tokensUsed()
                    );
                    candidates.add(retry);
                    consumedTokens += retry.tokensUsed();
                } catch (ExternalApiException ex) {
                    log.warn("Generic optimize retry attempt unavailable: cvId={}, error={}", cv.getId(), ex.getMessage());
                }
            }

            CandidateEvaluation selected = selectBestCandidate(baseline, candidates);
            if ("baseline".equals(selected.label())) {
                log.warn("Generic optimization attempts were regressive; preserving baseline CV for safety: cvId={}", cv.getId());
            }

            CVVersion version = new CVVersion();
            version.setCv(cv);
            version.setVersionType(CVVersionType.GENERIC_OPTIMIZED);
            version.setTailoredContent(selected.content());
            version.setAtsScore(selected.analysis().overallScore());
            version.setKeywordMatchRate(selected.keywordMatchRate());
            version.setAtsAnalysis(selected.analysis().rawJson());
            version.setDiffContent(diffService.serializeCompactSnapshot(selected.diff()));
            version.setCompletenessAnalysis(selected.completenessJson());
            version.setGeneratedByAI(!"baseline".equals(selected.label()));
            version.setProcessingStatus(ProcessingStatus.COMPLETED);
            version = cvVersionRepository.save(version);

            if (cv.getAtsScore() == null || selected.analysis().overallScore() > cv.getAtsScore()) {
                cv.setAtsScore(selected.analysis().overallScore());
                candidateCVRepository.save(cv);
            }

            try {
                profileTipService.generateTipsForCv(cv.getUserId(), cv.getId());
            } catch (Exception tipException) {
                log.warn("Profile tip generation failed after generic optimization: cvId={}, error={}", cv.getId(), tipException.getMessage());
            }

            int durationMs = (int) Duration.ofNanos(System.nanoTime() - started).toMillis();
            auditLogService.updateLog(
                    optimizeLog.getId(),
                    ProcessingStatus.COMPLETED,
                    consumedTokens,
                    durationMs,
                    null
            );
            return cvVersionMapper.toResponse(version);
        } catch (Exception e) {
            log.error("CV generic optimization failed: cvId={}, error={}", cv.getId(), e.getMessage());
            auditLogService.updateLog(
                    optimizeLog.getId(),
                    ProcessingStatus.FAILED,
                    null,
                    (int) Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    truncate(e.getMessage(), 500)
            );
            CVVersion failedVersion = new CVVersion();
            failedVersion.setCv(cv);
            failedVersion.setVersionType(CVVersionType.GENERIC_OPTIMIZED);
            failedVersion.setTailoredContent(cv.getParsedContent());
            failedVersion.setGeneratedByAI(true);
            failedVersion.setProcessingStatus(ProcessingStatus.FAILED);
            cvVersionRepository.save(failedVersion);
            if (e instanceof ExternalApiException runtime) {
                throw runtime;
            }
            throw new ExternalApiException("AI service temporarily unavailable", e);
        }
    }

    private record CandidateEvaluation(
            String label,
            String content,
            AtsAnalysisResult analysis,
            BigDecimal keywordMatchRate,
            String completenessJson,
            DiffResult diff,
            double qualityScore,
            int tokensUsed
    ) {
    }

    private CandidateEvaluation evaluateCandidate(
            String label,
            String originalContent,
            String candidateContent,
            String jobDescription,
            List<String> keywords,
            int generationTokens
    ) {
        AtsAnalysisResult analysis;
        try {
            analysis = atsScoreService.computeFullAnalysis(candidateContent, jobDescription, keywords);
        } catch (ExternalApiException ex) {
            log.warn("ATS analysis unavailable for candidate '{}', using fallback: error={}", label, ex.getMessage());
            analysis = buildFallbackAnalysis(candidateContent, keywords, ex.getMessage());
        }

        BigDecimal keywordMatchRate = atsScoreService.computeKeywordMatchRate(candidateContent, keywords);
        String completenessJson = computeCompletenessJson(candidateContent);
        DiffResult diff = diffService.computeDiff(originalContent, candidateContent);
        double qualityScore = computeQualityScore(candidateContent, analysis, keywordMatchRate);

        return new CandidateEvaluation(
                label,
                candidateContent,
                analysis,
                keywordMatchRate,
                completenessJson,
                diff,
                qualityScore,
                generationTokens + Math.max(0, analysis.tokensUsed())
        );
    }

    private CandidateEvaluation selectBestCandidate(CandidateEvaluation baseline, List<CandidateEvaluation> candidates) {
        CandidateEvaluation best = baseline;

        for (CandidateEvaluation candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (isRegression(candidate, baseline)) {
                continue;
            }
            if (candidate.qualityScore() > best.qualityScore() + 0.4d) {
                best = candidate;
            }
        }

        if (isRegression(best, baseline)) {
            return baseline;
        }
        return best;
    }

    private boolean isRegression(CandidateEvaluation candidate, CandidateEvaluation baseline) {
        if (candidate == null) {
            return true;
        }

        if (candidate.analysis().overallScore() + 2 < baseline.analysis().overallScore()) {
            return true;
        }

        BigDecimal keywordTolerance = baseline.keywordMatchRate().subtract(BigDecimal.valueOf(0.03));
        if (candidate.keywordMatchRate().compareTo(keywordTolerance) < 0) {
            return true;
        }

        if (countArrayItems(candidate.content(), "experience") < countArrayItems(baseline.content(), "experience")) {
            return true;
        }

        if (countArrayItems(candidate.content(), "education") < countArrayItems(baseline.content(), "education")) {
            return true;
        }

        if (countArrayItems(candidate.content(), "skills") + 2 < countArrayItems(baseline.content(), "skills")) {
            return true;
        }

        return false;
    }

    private double computeQualityScore(String cvJson, AtsAnalysisResult analysis, BigDecimal keywordMatchRate) {
        double atsScore = analysis.overallScore();
        double keywordScore = keywordMatchRate.multiply(BigDecimal.valueOf(100)).doubleValue();
        double structureScore = computeStructureScore(cvJson);
        return (atsScore * 0.55d) + (keywordScore * 0.35d) + (structureScore * 0.10d);
    }

    private double computeStructureScore(String cvJson) {
        JsonNode root = readJsonObject(cvJson);
        if (root == null || !root.isObject()) {
            return 0d;
        }

        double score = 0d;

        String summary = firstNonBlankText(root, List.of("summary", "professionalSummary", "profile", "about"));
        if (summary != null && !summary.isBlank()) {
            score += 20d;
        }

        int skillsCount = countArrayItems(cvJson, "skills");
        if (skillsCount >= 8) {
            score += 30d;
        } else if (skillsCount >= 4) {
            score += 18d;
        } else if (skillsCount > 0) {
            score += 8d;
        }

        int experienceCount = countArrayItems(cvJson, "experience");
        if (experienceCount >= 2) {
            score += 35d;
        } else if (experienceCount == 1) {
            score += 20d;
        }

        int educationCount = countArrayItems(cvJson, "education");
        if (educationCount >= 1) {
            score += 15d;
        }

        return Math.max(0d, Math.min(100d, score));
    }

    private int countArrayItems(String cvJson, String fieldName) {
        JsonNode root = readJsonObject(cvJson);
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.isArray()) {
            int nonEmpty = 0;
            for (JsonNode item : node) {
                if (item == null || item.isNull()) {
                    continue;
                }
                if (item.isTextual() && item.asText("").isBlank()) {
                    continue;
                }
                nonEmpty++;
            }
            return nonEmpty;
        }
        if (node.isTextual() && !node.asText("").isBlank()) {
            return 1;
        }
        return 0;
    }

    private String buildTailorRetryDescription(String rawJobDescription) {
        return rawJobDescription + "\n\nAdditional constraints: preserve all original experience and education entries, keep factual accuracy, and improve ATS keyword alignment naturally without keyword stuffing.";
    }

    private AtsAnalysisResult buildFallbackAnalysis(String cvJson, List<String> keywords, String reason) {
        BigDecimal matchRate = atsScoreService.computeKeywordMatchRate(cvJson, keywords);
        int keywordScore = matchRate.multiply(BigDecimal.valueOf(100)).intValue();
        int overall = Math.max(0, Math.min(100, keywordScore));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overallScore", overall);
        payload.put("breakdown", Map.of(
                "keywordMatch", keywordScore,
                "experienceRelevance", Math.max(40, overall - 10),
                "skillsAlignment", Math.max(40, overall - 5),
                "educationCertifications", Math.max(35, overall - 15)
        ));
        payload.put("matchedKeywords", List.of());
        payload.put("missingKeywords", keywords);
        payload.put("strengthSummary", "Fallback local scoring was used because AI analysis was unavailable.");
        payload.put("improvementSuggestions", List.of(
                "Retry tailoring later to get full AI-based analysis.",
                "Review missing keywords and align CV wording where accurate."
        ));
        payload.put("fallbackReason", reason == null ? "AI unavailable" : reason);

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            // If serialization unexpectedly fails, return a compact static JSON fallback.
            rawJson = "{\"overallScore\":" + overall + ",\"breakdown\":{\"keywordMatch\":" + keywordScore + "}}";
        }

        return new AtsAnalysisResult(
                overall,
                keywordScore,
                Math.max(40, overall - 10),
                Math.max(40, overall - 5),
                Math.max(35, overall - 15),
                List.of(),
                keywords,
                "Fallback local scoring was used because AI analysis was unavailable.",
                List.of(
                        "Retry tailoring later to get full AI-based analysis.",
                        "Review missing keywords and align CV wording where accurate."
                ),
                rawJson,
                0
        );
    }

    @Transactional(readOnly = true)
    public List<CvVersionResponse> getVersionsForCv(String cvId) {
        UUID parsedCvId = parseUuid(cvId, "cvId");
        candidateCVRepository.findByIdAndUserId(parsedCvId, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));
        return cvVersionMapper.toResponseList(cvVersionRepository.findByCvId(parsedCvId));
    }

    @Transactional(readOnly = true)
    public CvVersionResponse getVersionById(String versionId) {
        UUID parsedVersionId = parseUuid(versionId, "versionId");
        CVVersion version = cvVersionRepository.findById(parsedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("CV version not found"));
        verifyOwnership(version);
        return cvVersionMapper.toResponse(version);
    }

    @Transactional
    public byte[] exportVersionAsPdf(String versionId) {
        UUID parsedVersionId = parseUuid(versionId, "versionId");
        CVVersion version = cvVersionRepository.findById(parsedVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("CV version not found"));
        verifyOwnership(version);

        byte[] pdfBytes = pdfExportService.exportCvToPdf(
            version.getTailoredContent(),
            version.getCv().getParsedContent(),
            version.getCv().getOriginalFileName()
        );
        String storagePath = pdfExportService.savePdfToStorage(pdfBytes, AppConstants.currentUserId(), parsedVersionId);
        version.setExportedFileUrl(storagePath);
        cvVersionRepository.save(version);
        return pdfBytes;
    }

    @Transactional
    public void deactivateCv(String cvId) {
        UUID parsedCvId = parseUuid(cvId, "cvId");
        CandidateCV cv = candidateCVRepository.findByIdAndUserId(parsedCvId, AppConstants.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("CV not found"));
        cv.setIsActive(false);
        candidateCVRepository.save(cv);
    }

    private void deactivateExistingActiveCv(UUID userId) {
        candidateCVRepository.findByUserIdAndIsActiveTrue(userId)
                .forEach(existingCv -> {
                    if (!Boolean.FALSE.equals(existingCv.getIsActive())) {
                        existingCv.setIsActive(Boolean.FALSE);
                        candidateCVRepository.save(existingCv);
                    }
                });
    }

    private String extractRawText(String filePath) {
        Path path = fileStorageService.resolveFullPath(filePath);
        try (InputStream inputStream = java.nio.file.Files.newInputStream(path);
             org.apache.tika.io.TikaInputStream tikaInputStream = org.apache.tika.io.TikaInputStream.get(inputStream)) {
            AutoDetectParser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(-1);
            parser.parse(tikaInputStream, handler, new Metadata(), new ParseContext());
            return handler.toString();
        } catch (Exception e) {
            throw new ValidationException("Unable to read CV content from uploaded file");
        }
    }

    private FileFormat resolveFileFormat(String fileName) {
        String lower = safeFileName(fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return FileFormat.DOCX;
        }
        return FileFormat.PDF;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "uploaded-cv.pdf";
        }
        return fileName;
    }

    private List<String> readKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(keywordsJson, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new ValidationException("Invalid extractedKeywords format for job offer");
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new ValidationException("Invalid " + fieldName + " UUID value");
        }
    }

    private void verifyOwnership(CVVersion version) {
        if (!AppConstants.currentUserId().equals(version.getCv().getUserId())) {
            throw new ResourceNotFoundException("CV version not found");
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

    private String normalizeCvJson(String primaryCvJson, String fallbackCvJson) {
        JsonNode primary = readJsonObject(primaryCvJson);
        JsonNode fallback = readJsonObject(fallbackCvJson);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("name", firstNonBlankText(primary, fallback, List.of("name", "fullName", "candidateName"), "Candidate"));
        normalized.put("email", emptyToNull(firstNonBlankText(primary, fallback, List.of("email", "mail", "emailAddress"), "")));
        normalized.put("phone", emptyToNull(firstNonBlankText(primary, fallback, List.of("phone", "phoneNumber", "mobile"), "")));
        normalized.put("summary", firstNonBlankText(primary, fallback, List.of("summary", "professionalSummary", "profile", "about"), ""));

        List<String> skills = uniqueNormalizedList(readTextList(primary, List.of("skills", "technicalSkills", "keySkills", "coreSkills")));
        if (skills.isEmpty()) {
            skills = uniqueNormalizedList(readTextList(fallback, List.of("skills", "technicalSkills", "keySkills", "coreSkills")));
        }
        ArrayNode skillsNode = normalized.putArray("skills");
        skills.forEach(skillsNode::add);

        ArrayNode experienceNode = normalized.putArray("experience");
        List<ObjectNode> normalizedExperience = pickBestExperience(normalizeExperienceList(primary), normalizeExperienceList(fallback));
        normalizedExperience.forEach(experienceNode::add);

        ArrayNode educationNode = normalized.putArray("education");
        List<ObjectNode> normalizedEducation = pickBestEducation(normalizeEducationList(primary), normalizeEducationList(fallback));
        normalizedEducation.forEach(educationNode::add);

        String primarySummary = firstNonBlankText(primary, List.of("summary", "professionalSummary", "profile", "about"));
        String fallbackSummary = firstNonBlankText(fallback, List.of("summary", "professionalSummary", "profile", "about"));
        if (shouldPreferFallbackSummary(primarySummary, fallbackSummary)) {
            normalized.put("summary", fallbackSummary);
        }

        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            if (primaryCvJson != null && !primaryCvJson.isBlank()) {
                return primaryCvJson;
            }
            if (fallbackCvJson != null && !fallbackCvJson.isBlank()) {
                return fallbackCvJson;
            }
            return "{}";
        }
    }

    private JsonNode readJsonObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(rawJson);
            if (parsed != null && parsed.isObject()) {
                return parsed;
            }
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String firstNonBlankText(JsonNode primary, JsonNode fallback, List<String> keys, String defaultValue) {
        String fromPrimary = firstNonBlankText(primary, keys);
        if (fromPrimary != null) {
            return fromPrimary;
        }
        String fromFallback = firstNonBlankText(fallback, keys);
        if (fromFallback != null) {
            return fromFallback;
        }
        return defaultValue;
    }

    private String firstNonBlankText(JsonNode node, List<String> keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = normalizeWhitespace(value.asText(""));
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private List<String> readTextList(JsonNode node, List<String> keys) {
        if (node == null || !node.isObject()) {
            return List.of();
        }

        for (String key : keys) {
            JsonNode field = node.get(key);
            if (field == null || field.isNull()) {
                continue;
            }

            if (field.isArray()) {
                List<String> values = new java.util.ArrayList<>();
                field.forEach(item -> {
                    if (item == null || item.isNull()) {
                        return;
                    }
                    if (item.isTextual()) {
                        values.addAll(splitTextValues(item.asText("")));
                        return;
                    }
                    if (item.isObject()) {
                        String candidate = firstNonBlankText(item, List.of("name", "skill", "value", "text"));
                        if (candidate != null) {
                            values.addAll(splitTextValues(candidate));
                        }
                    }
                });
                if (!values.isEmpty()) {
                    return values;
                }
                continue;
            }

            if (field.isTextual()) {
                List<String> values = splitTextValues(field.asText(""));
                if (!values.isEmpty()) {
                    return values;
                }
            }
        }

        return List.of();
    }

    private List<String> splitTextValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace("\r", "\n");
        String[] parts = normalized.split("\\n|,|\\u2022|\\u00B7|\\|");
        List<String> values = new java.util.ArrayList<>();
        for (String part : parts) {
            String cleaned = normalizeWhitespace(stripBulletPrefix(part));
            if (!cleaned.isBlank()) {
                values.add(cleaned);
            }
        }
        return values;
    }

    private List<String> uniqueNormalizedList(List<String> values) {
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = normalizeWhitespace(stripBulletPrefix(value));
            if (!cleaned.isBlank()) {
                deduped.add(cleaned);
            }
        }
        return List.copyOf(deduped);
    }

    private List<ObjectNode> normalizeExperienceList(JsonNode root) {
        List<ObjectNode> normalized = new java.util.ArrayList<>();
        JsonNode source = pickFirstArray(root, List.of("experience", "workExperience", "professionalExperience", "employmentHistory"));
        if (source != null) {
            source.forEach(item -> {
                if (item == null || item.isNull()) {
                    return;
                }

                if (item.isObject()) {
                    String title = firstNonBlankText(item, List.of("title", "role", "position", "jobTitle"));
                    String company = firstNonBlankText(item, List.of("company", "employer", "organization"));
                    String duration = firstNonBlankText(item, List.of("duration", "period", "dates", "dateRange"));
                    String descriptionRaw = firstNonBlankText(item, List.of("description", "responsibilities", "achievements", "details", "summary"));

                    if ((title == null || title.isBlank())
                            && (company == null || company.isBlank())
                            && (descriptionRaw == null || descriptionRaw.isBlank())) {
                        return;
                    }

                    ObjectNode normalizedItem = objectMapper.createObjectNode();
                    normalizedItem.put("title", defaultIfBlank(title, "Role"));
                    normalizedItem.put("company", defaultIfBlank(company, "Company"));
                    normalizedItem.put("duration", defaultIfBlank(duration, ""));
                    normalizedItem.put("description", normalizeDescription(descriptionRaw));
                    normalized.add(normalizedItem);
                    return;
                }

                if (item.isTextual()) {
                    normalized.addAll(parseExperienceTextBlock(item.asText("")));
                }
            });
        }

        if (!normalized.isEmpty()) {
            return normalized;
        }

        String textBlock = firstNonBlankText(root,
                List.of("experience", "workExperience", "professionalExperience", "employmentHistory"));
        if (textBlock != null && !textBlock.isBlank()) {
            return parseExperienceTextBlock(textBlock);
        }

        return normalized;
    }

    private List<ObjectNode> normalizeEducationList(JsonNode root) {
        List<ObjectNode> normalized = new java.util.ArrayList<>();
        JsonNode source = pickFirstArray(root, List.of("education", "academicBackground", "academics"));
        if (source != null) {
            source.forEach(item -> {
                if (item == null || item.isNull()) {
                    return;
                }

                if (item.isObject()) {
                    String degree = firstNonBlankText(item, List.of("degree", "qualification", "title"));
                    String institution = firstNonBlankText(item, List.of("institution", "school", "university", "college"));
                    String year = firstNonBlankText(item, List.of("year", "graduationYear", "date", "period"));

                    if ((degree == null || degree.isBlank())
                            && (institution == null || institution.isBlank())
                            && (year == null || year.isBlank())) {
                        return;
                    }

                    ObjectNode normalizedItem = objectMapper.createObjectNode();
                    normalizedItem.put("degree", defaultIfBlank(degree, "Degree"));
                    normalizedItem.put("institution", defaultIfBlank(institution, "Institution"));
                    normalizedItem.put("year", defaultIfBlank(year, ""));
                    normalized.add(normalizedItem);
                    return;
                }

                if (item.isTextual()) {
                    normalized.add(parseEducationLine(item.asText("")));
                }
            });
        }

        if (!normalized.isEmpty()) {
            return normalized;
        }

        String textBlock = firstNonBlankText(root, List.of("education", "academicBackground", "academics"));
        if (textBlock != null && !textBlock.isBlank()) {
            List<String> lines = splitLines(textBlock);
            List<ObjectNode> parsed = new java.util.ArrayList<>();
            for (String line : lines) {
                if (!line.isBlank()) {
                    parsed.add(parseEducationLine(line));
                }
            }
            return parsed;
        }

        return normalized;
    }

    private List<ObjectNode> parseExperienceTextBlock(String rawText) {
        List<String> lines = splitLines(rawText);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<ObjectNode> entries = new java.util.ArrayList<>();
        ObjectNode current = null;
        StringBuilder description = new StringBuilder();

        for (String line : lines) {
            if (looksLikeExperienceHeader(line)) {
                if (current != null) {
                    current.put("description", normalizeDescription(description.toString()));
                    entries.add(current);
                }
                current = parseExperienceHeader(line);
                description = new StringBuilder();
                continue;
            }

            if (description.length() > 0) {
                description.append("\n");
            }
            description.append(line);
        }

        if (current != null) {
            current.put("description", normalizeDescription(description.toString()));
            entries.add(current);
            return entries;
        }

        ObjectNode single = objectMapper.createObjectNode();
        single.put("title", "Experience");
        single.put("company", "");
        single.put("duration", "");
        single.put("description", normalizeDescription(String.join("\n", lines)));
        return List.of(single);
    }

    private ObjectNode parseExperienceHeader(String line) {
        String cleaned = normalizeWhitespace(stripBulletPrefix(line));
        String duration = "";

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(19|20)\\d{2}(?:\\s*[-–]\\s*(?:present|now|(19|20)\\d{2}))?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(cleaned);
        if (matcher.find()) {
            duration = normalizeWhitespace(matcher.group());
            cleaned = normalizeWhitespace((cleaned.substring(0, matcher.start()) + " " + cleaned.substring(matcher.end())).trim());
            cleaned = cleaned.replaceAll("[,-]$", "").trim();
        }

        String title = cleaned;
        String company = "";
        String[] separators = new String[]{" - ", " – ", ",", " at "};
        for (String sep : separators) {
            String marker = sep.toLowerCase(Locale.ROOT);
            int idx = cleaned.toLowerCase(Locale.ROOT).indexOf(marker);
            if (idx > 0) {
                String left = cleaned.substring(0, idx).trim();
                String right = cleaned.substring(idx + sep.length()).trim();
                if (!left.isBlank() && !right.isBlank()) {
                    title = left;
                    company = right;
                    break;
                }
            }
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", defaultIfBlank(title, "Role"));
        node.put("company", defaultIfBlank(company, ""));
        node.put("duration", defaultIfBlank(duration, ""));
        node.put("description", "");
        return node;
    }

    private List<ObjectNode> pickBestExperience(List<ObjectNode> primary, List<ObjectNode> fallback) {
        if (primary == null || primary.isEmpty()) {
            return fallback == null ? List.of() : fallback;
        }
        if (fallback == null || fallback.isEmpty()) {
            return primary;
        }

        int primaryScore = scoreExperience(primary);
        int fallbackScore = scoreExperience(fallback);
        if (fallbackScore > primaryScore) {
            return fallback;
        }
        return primary;
    }

    private int scoreExperience(List<ObjectNode> experienceList) {
        int score = 0;
        for (ObjectNode entry : experienceList) {
            String title = normalizeWhitespace(entry.path("title").asText(""));
            String company = normalizeWhitespace(entry.path("company").asText(""));
            String duration = normalizeWhitespace(entry.path("duration").asText(""));
            String description = normalizeWhitespace(entry.path("description").asText(""));

            if (!title.isBlank() && !"Role".equalsIgnoreCase(title)) {
                score += 2;
            }
            if (!company.isBlank() && !"Company".equalsIgnoreCase(company)) {
                score += 2;
            }
            if (!duration.isBlank()) {
                score += 1;
            }
            if (!description.isBlank() && !"-".equals(description)) {
                score += 3;
            }
        }
        return score;
    }

    private List<ObjectNode> pickBestEducation(List<ObjectNode> primary, List<ObjectNode> fallback) {
        if (primary == null || primary.isEmpty()) {
            return fallback == null ? List.of() : fallback;
        }
        if (fallback == null || fallback.isEmpty()) {
            return primary;
        }

        int primaryScore = scoreEducation(primary);
        int fallbackScore = scoreEducation(fallback);
        if (fallbackScore > primaryScore) {
            return fallback;
        }
        return primary;
    }

    private int scoreEducation(List<ObjectNode> educationList) {
        int score = 0;
        for (ObjectNode entry : educationList) {
            String degree = normalizeWhitespace(entry.path("degree").asText(""));
            String institution = normalizeWhitespace(entry.path("institution").asText(""));
            String year = normalizeWhitespace(entry.path("year").asText(""));

            if (!degree.isBlank() && !"Degree".equalsIgnoreCase(degree)) {
                score += 2;
            }
            if (!institution.isBlank() && !"Institution".equalsIgnoreCase(institution)) {
                score += 2;
            }
            if (!year.isBlank()) {
                score += 1;
            }
        }
        return score;
    }

    private boolean shouldPreferFallbackSummary(String primarySummary, String fallbackSummary) {
        if (fallbackSummary == null || fallbackSummary.isBlank()) {
            return false;
        }
        if (primarySummary == null || primarySummary.isBlank()) {
            return true;
        }

        String p = primarySummary.toLowerCase(Locale.ROOT);
        String f = fallbackSummary.toLowerCase(Locale.ROOT);
        boolean primaryLooksTargetCompanySpecific = p.contains("seeking") && p.contains(" at ");
        boolean fallbackIsNeutral = !f.contains(" at ");
        return primaryLooksTargetCompanySpecific && fallbackIsNeutral;
    }

    private ObjectNode parseEducationLine(String line) {
        String cleaned = normalizeWhitespace(stripBulletPrefix(line));
        String year = "";

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(19|20)\\d{2}(?:\\s*[-–]\\s*(19|20)\\d{2})?")
                .matcher(cleaned);
        if (matcher.find()) {
            year = normalizeWhitespace(matcher.group());
            cleaned = normalizeWhitespace((cleaned.substring(0, matcher.start()) + " " + cleaned.substring(matcher.end())).trim());
            cleaned = cleaned.replaceAll("^[,:\\-–\\s]+", "").trim();
        }

        String degree = cleaned;
        String institution = "";
        int commaIdx = cleaned.indexOf(',');
        if (commaIdx > 0 && commaIdx < cleaned.length() - 1) {
            degree = cleaned.substring(0, commaIdx).trim();
            institution = cleaned.substring(commaIdx + 1).trim();
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("degree", defaultIfBlank(degree, "Degree"));
        node.put("institution", defaultIfBlank(institution, ""));
        node.put("year", defaultIfBlank(year, ""));
        return node;
    }

    private List<String> splitLines(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        String normalized = rawText.replace("\r", "\n");
        String[] tokens = normalized.split("\\n");
        List<String> lines = new java.util.ArrayList<>();
        for (String token : tokens) {
            String line = normalizeWhitespace(stripBulletPrefix(token));
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private boolean looksLikeExperienceHeader(String line) {
        String value = normalizeWhitespace(line).toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return false;
        }
        boolean hasYear = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(value).find();
        boolean hasRoleToken = value.contains("intern")
                || value.contains("engineer")
                || value.contains("developer")
                || value.contains("manager")
                || value.contains("freelancer")
                || value.contains("consultant")
                || value.contains("analyst")
                || value.contains("designer")
                || value.contains("lead")
                || value.contains("assistant")
                || value.contains("officer")
                || value.contains("specialist")
                || value.contains("technician")
                || value.contains("administrator")
                || value.contains("director")
                || value.contains("coordinator")
                || value.contains("teacher")
                || value.contains("student");
        return hasYear || hasRoleToken;
    }

    private JsonNode pickFirstArray(JsonNode root, List<String> keys) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode candidate = root.get(key);
            if (candidate != null && candidate.isArray() && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String cleaned = normalizeWhitespace(raw.replace("\r", "\n"));
        List<String> bullets = splitDescriptionBullets(cleaned);
        if (bullets.isEmpty()) {
            return cleaned;
        }
        return String.join("\n", bullets);
    }

    private List<String> splitDescriptionBullets(String description) {
        String[] parts = description.split("\\n|(?<=\\.)\\s+|\\s*;\\s*");
        List<String> bullets = new java.util.ArrayList<>();
        for (String part : parts) {
            String cleaned = normalizeWhitespace(stripBulletPrefix(part));
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.length() < 3) {
                continue;
            }
            if (!cleaned.endsWith(".")) {
                cleaned = cleaned + ".";
            }
            bullets.add("- " + cleaned);
            if (bullets.size() == 6) {
                break;
            }
        }
        return bullets;
    }

    private String stripBulletPrefix(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        while (cleaned.startsWith("-") || cleaned.startsWith("*") || cleaned.startsWith(".") || cleaned.startsWith("•")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String computeCompletenessJson(String cvJson) {
        try {
            CompletenessResult completeness = completenessService.computeCompleteness(cvJson);
            return completenessService.serializeCompleteness(completeness);
        } catch (Exception e) {
            log.warn("Completeness analysis failed, using fallback heuristic: error={}", e.getMessage());
            return completenessService.serializeCompleteness(buildFallbackCompleteness(cvJson));
        }
    }

    private CompletenessResult buildFallbackCompleteness(String cvJson) {
        Map<String, CompletenessResult.SectionCompleteness> sections = new LinkedHashMap<>();
        Map<String, Object> parsed = Map.of();
        try {
            parsed = objectMapper.readValue(cvJson, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            // keep empty fallback map
        }

        boolean hasSummary = parsed.containsKey("summary") && parsed.get("summary") != null && !parsed.get("summary").toString().isBlank();
        boolean hasSkills = parsed.containsKey("skills") && parsed.get("skills") != null && !parsed.get("skills").toString().equals("[]");
        boolean hasExperience = parsed.containsKey("experience") && parsed.get("experience") != null && !parsed.get("experience").toString().equals("[]");
        boolean hasEducation = parsed.containsKey("education") && parsed.get("education") != null && !parsed.get("education").toString().equals("[]");
        boolean hasContact = parsed.containsKey("name") && parsed.get("name") != null && !parsed.get("name").toString().isBlank();

        sections.put("contactInfo", new CompletenessResult.SectionCompleteness(hasContact, hasContact ? 90 : 20, hasContact ? "Contact details are present" : "Add a visible name and contact details"));
        sections.put("summary", new CompletenessResult.SectionCompleteness(hasSummary, hasSummary ? 80 : 0, hasSummary ? "Summary is present" : "Add a professional summary"));
        sections.put("skills", new CompletenessResult.SectionCompleteness(hasSkills, hasSkills ? 80 : 0, hasSkills ? "Skills section is present" : "Add a skills section"));
        sections.put("experience", new CompletenessResult.SectionCompleteness(hasExperience, hasExperience ? 85 : 0, hasExperience ? "Experience section is present" : "Add experience entries"));
        sections.put("education", new CompletenessResult.SectionCompleteness(hasEducation, hasEducation ? 75 : 0, hasEducation ? "Education section is present" : "Add education details"));

        int score = (sections.get("contactInfo").score() + sections.get("summary").score() + sections.get("skills").score() + sections.get("experience").score() + sections.get("education").score()) / 5;
        List<String> missing = new java.util.ArrayList<>();
        if (!hasContact) missing.add("contactInfo");
        if (!hasSummary) missing.add("summary");
        if (!hasSkills) missing.add("skills");
        if (!hasExperience) missing.add("experience");
        if (!hasEducation) missing.add("education");

        List<String> weak = new java.util.ArrayList<>();
        if (!hasSummary) weak.add("Add a stronger professional summary.");
        if (!hasSkills) weak.add("List 8+ role-relevant skills.");
        if (!hasExperience) weak.add("Add quantified experience bullets.");
        if (!hasEducation) weak.add("Include clear education details.");

        String verdict = score >= 80 ? "Strong" : score >= 60 ? "Moderate" : "Needs improvement";
        return new CompletenessResult(score, sections, missing, weak, verdict);
    }
}
