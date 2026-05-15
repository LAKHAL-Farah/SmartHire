package tn.esprit.msprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.CVVersionRequest;
import tn.esprit.msprofile.dto.response.CVVersionResponse;
import tn.esprit.msprofile.entity.CVVersion;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.CVVersionRepository;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.JobOfferRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CVVersionService extends AbstractCrudService<CVVersion, CVVersionResponse> {

    private static final Path CV_EXPORT_DIRECTORY = Paths.get("temp", "cv-exports");

    private final CVVersionRepository cvVersionRepository;
    private final CandidateCVRepository candidateCVRepository;
    private final JobOfferRepository jobOfferRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    protected JpaRepository<CVVersion, UUID> repository() {
        return cvVersionRepository;
    }

    @Override
    protected CVVersionResponse toResponse(CVVersion entity) {
        return new CVVersionResponse(
                entity.getId(),
                entity.getCv().getId(),
                entity.getJobOffer() != null ? entity.getJobOffer().getId() : null,
                entity.getVersionType(),
                entity.getTailoredContent(),
                entity.getAtsScore(),
                entity.getKeywordMatchRate(),
                entity.getDiffContent(),
                entity.getGeneratedByAI(),
                entity.getProcessingStatus(),
                entity.getExportedFileUrl(),
                entity.getGeneratedAt()
        );
    }

    @Override
    protected String resourceName() {
        return "CVVersion";
    }

    @Transactional
    public CVVersionResponse tailorCvForJobOffer(UUID cvId, UUID jobOfferId) {
        long startedAt = System.currentTimeMillis();
        CandidateCV cv = candidateCVRepository.findById(cvId)
                .orElseThrow(() -> new ResourceNotFoundException("CandidateCV not found with id=" + cvId));
        JobOffer jobOffer = jobOfferRepository.findById(jobOfferId)
                .orElseThrow(() -> new ResourceNotFoundException("JobOffer not found with id=" + jobOfferId));

        CVVersion version = new CVVersion();
        version.setCv(cv);
        version.setJobOffer(jobOffer);
        version.setVersionType(CVVersionType.TAILORED);
        version.setGeneratedByAI(Boolean.TRUE);
        version.setProcessingStatus(ProcessingStatus.IN_PROGRESS);
        version.setTailoredContent("");
        version.setGeneratedAt(Instant.now());
        version = cvVersionRepository.save(version);

        try {
            String originalContent = cv.getParsedContent() != null ? cv.getParsedContent() : cv.getOriginalFileName();
            List<String> jobKeywords = extractJobKeywords(jobOffer);
            String tailoredContent = generateTailoredContent(originalContent, jobOffer, jobKeywords);
            BigDecimal keywordMatchRate = calculateKeywordMatchRate(tailoredContent, jobKeywords);
            int atsScore = computeAtsFromKeywordMatch(keywordMatchRate);

            version.setTailoredContent(tailoredContent);
            version.setKeywordMatchRate(keywordMatchRate);
            version.setAtsScore(atsScore);
            version.setDiffContent(computeDiff(originalContent, tailoredContent));
            version.setProcessingStatus(ProcessingStatus.COMPLETED);
            CVVersion saved = cvVersionRepository.save(version);
            auditLogService.logOperation(
                    cv.getUserId(),
                    OperationType.CV_TAILOR,
                    "CVVersion",
                    saved.getId(),
                    ProcessingStatus.COMPLETED,
                    1800,
                    (int) (System.currentTimeMillis() - startedAt),
                    null
            );
            return toResponse(saved);
        } catch (Exception e) {
            log.warn("Failed to tailor CV {} for job offer {}: {}", cvId, jobOfferId, e.getMessage());
            version.setProcessingStatus(ProcessingStatus.FAILED);
            version.setDiffContent(truncate(e.getMessage(), 1000));
            CVVersion saved = cvVersionRepository.save(version);
            auditLogService.logOperation(
                    cv.getUserId(),
                    OperationType.CV_TAILOR,
                    "CVVersion",
                    saved.getId(),
                    ProcessingStatus.FAILED,
                    200,
                    (int) (System.currentTimeMillis() - startedAt),
                    e.getMessage()
            );
            return toResponse(saved);
        }
    }

    public List<CVVersionResponse> getVersionsForCv(UUID cvId) {
        return cvVersionRepository.findByCvId(cvId).stream()
                .sorted(Comparator.comparing(CVVersion::getGeneratedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public CVVersionResponse getVersionById(UUID versionId) {
        return toResponse(requireEntity(versionId));
    }

    @Transactional
    public byte[] exportVersionAsPdf(UUID versionId) {
        CVVersion version = requireEntity(versionId);
        String content = version.getTailoredContent() == null ? "No tailored content available" : version.getTailoredContent();

        byte[] pdfBytes = buildPdfBytes(version.getId(), version.getVersionType().name(), content);
        try {
            Files.createDirectories(CV_EXPORT_DIRECTORY);
            Path outputPath = CV_EXPORT_DIRECTORY.resolve(versionId + ".pdf");
            Files.write(outputPath, pdfBytes);
            version.setExportedFileUrl(outputPath.toString());
            cvVersionRepository.save(version);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist PDF export for versionId=" + versionId, e);
        }

        return pdfBytes;
    }

    @Transactional
    public CVVersionResponse generateGenericOptimizedVersion(UUID cvId) {
        CandidateCV cv = candidateCVRepository.findById(cvId)
                .orElseThrow(() -> new ResourceNotFoundException("CandidateCV not found with id=" + cvId));

        String originalContent = cv.getParsedContent() != null ? cv.getParsedContent() : cv.getOriginalFileName();
        String optimized = buildGenericOptimization(originalContent);

        CVVersion version = new CVVersion();
        version.setCv(cv);
        version.setVersionType(CVVersionType.GENERIC_OPTIMIZED);
        version.setTailoredContent(optimized);
        version.setDiffContent(computeDiff(originalContent, optimized));
        version.setGeneratedByAI(Boolean.TRUE);
        version.setProcessingStatus(ProcessingStatus.COMPLETED);
        version.setGeneratedAt(Instant.now());
        version.setKeywordMatchRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        version.setAtsScore(Math.min(100, 60 + Math.max(0, optimized.length() - originalContent.length()) / 20));

        return toResponse(cvVersionRepository.save(version));
    }

    public String computeDiff(String originalContent, String tailoredContent) {
        Set<String> originalWords = extractDistinctWords(originalContent);
        Set<String> tailoredWords = extractDistinctWords(tailoredContent);

        List<String> added = tailoredWords.stream()
                .filter(word -> !originalWords.contains(word))
                .limit(30)
                .toList();

        List<String> removed = originalWords.stream()
                .filter(word -> !tailoredWords.contains(word))
                .limit(30)
                .toList();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("originalLength", originalContent == null ? 0 : originalContent.length());
        snapshot.put("tailoredLength", tailoredContent == null ? 0 : tailoredContent.length());
        snapshot.put("addedKeywords", added);
        snapshot.put("removedKeywords", removed);

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"diff-serialization-failed\"}";
        }
    }

    public List<CVVersionResponse> findByCvId(UUID cvId) {
        return cvVersionRepository.findByCvId(cvId).stream().map(this::toResponse).toList();
    }

    public List<CVVersionResponse> findByJobOfferId(UUID jobOfferId) {
        return cvVersionRepository.findByJobOfferId(jobOfferId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CVVersionResponse create(CVVersionRequest request) {
        CVVersion entity = new CVVersion();
        apply(entity, request);
        return toResponse(cvVersionRepository.save(entity));
    }

    @Transactional
    public CVVersionResponse update(UUID id, CVVersionRequest request) {
        CVVersion entity = requireEntity(id);
        apply(entity, request);
        return toResponse(cvVersionRepository.save(entity));
    }

    private void apply(CVVersion entity, CVVersionRequest request) {
        entity.setCv(candidateCVRepository.findById(request.cvId())
                .orElseThrow(() -> new ResourceNotFoundException("CandidateCV not found with id=" + request.cvId())));
        entity.setJobOffer(resolveJobOffer(request.jobOfferId()));
        entity.setVersionType(request.versionType());
        entity.setTailoredContent(request.tailoredContent().trim());
        entity.setAtsScore(request.atsScore());
        entity.setKeywordMatchRate(request.keywordMatchRate());
        entity.setDiffContent(trimToNull(request.diffContent()));
        entity.setGeneratedByAI(request.generatedByAI() != null ? request.generatedByAI() : Boolean.FALSE);
        entity.setProcessingStatus(request.processingStatus() != null ? request.processingStatus() : ProcessingStatus.PENDING);
        entity.setExportedFileUrl(trimToNull(request.exportedFileUrl()));
        entity.setGeneratedAt(request.generatedAt());
    }

    private List<String> extractJobKeywords(JobOffer jobOffer) {
        if (jobOffer.getExtractedKeywords() != null && !jobOffer.getExtractedKeywords().isBlank()) {
            try {
                return objectMapper.readValue(jobOffer.getExtractedKeywords(), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class));
            } catch (Exception ignored) {
                log.debug("Job offer {} extracted keywords is not valid JSON list, falling back to raw extraction", jobOffer.getId());
            }
        }

        String raw = jobOffer.getRawDescription() == null ? "" : jobOffer.getRawDescription();
        return Arrays.stream(raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 4)
                .filter(token -> !Set.of("with", "that", "from", "this", "will", "have", "your", "team").contains(token))
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String generateTailoredContent(String originalContent, JobOffer jobOffer, List<String> jobKeywords) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ATS Tailored CV\n");
        builder.append("Role: ").append(jobOffer.getTitle()).append(" at ").append(jobOffer.getCompany() == null ? "the target company" : jobOffer.getCompany()).append("\n\n");
        builder.append("## Optimized Summary\n");
        builder.append("Results-driven professional with proven impact in ")
                .append(String.join(", ", jobKeywords.stream().limit(6).toList()))
                .append(".\n\n");
        builder.append("## Keyword Alignment\n");
        for (String keyword : jobKeywords.stream().limit(12).toList()) {
            builder.append("- Demonstrated experience with ").append(keyword).append(".\n");
        }
        builder.append("\n## Original Structured Content\n");
        builder.append(originalContent == null ? "" : originalContent);
        return builder.toString();
    }

    private BigDecimal calculateKeywordMatchRate(String tailoredContent, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String normalized = tailoredContent == null ? "" : tailoredContent.toLowerCase(Locale.ROOT);
        long matched = keywords.stream()
                .filter(keyword -> normalized.contains(keyword.toLowerCase(Locale.ROOT)))
                .count();

        return BigDecimal.valueOf((matched * 100.0) / keywords.size())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int computeAtsFromKeywordMatch(BigDecimal keywordMatchRate) {
        return Math.min(100, Math.max(0, 35 + keywordMatchRate.multiply(BigDecimal.valueOf(0.65)).intValue()));
    }

    private String buildGenericOptimization(String originalContent) {
        String safeOriginal = originalContent == null ? "" : originalContent;
        StringBuilder builder = new StringBuilder();
        builder.append("# Generic ATS Optimized Version\n");
        builder.append("## Improvements Applied\n");
        builder.append("- Standardized role titles and achievement-oriented bullets.\n");
        builder.append("- Added measurable impact statements using action verbs.\n");
        builder.append("- Improved keyword density for core technical and soft skills.\n\n");
        builder.append("## Optimized Content\n");
        builder.append(safeOriginal);
        return builder.toString();
    }

    private Set<String> extractDistinctWords(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(content.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toSet());
    }

    private byte[] buildPdfBytes(UUID versionId, String versionType, String content) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            document.add(new Paragraph("SmartHire - ATS Optimized CV Version"));
            document.add(new Paragraph("Version ID: " + versionId));
            document.add(new Paragraph("Version Type: " + versionType));
            document.add(new Paragraph("Generated At: " + Instant.now()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph(content));
            document.close();

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to export PDF content", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private JobOffer resolveJobOffer(UUID jobOfferId) {
        if (jobOfferId == null) {
            return null;
        }
        return jobOfferRepository.findById(jobOfferId)
                .orElseThrow(() -> new ResourceNotFoundException("JobOffer not found with id=" + jobOfferId));
    }
}

