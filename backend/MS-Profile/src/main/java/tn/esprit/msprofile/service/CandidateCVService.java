package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msprofile.dto.request.CandidateCVRequest;
import tn.esprit.msprofile.dto.response.CandidateCVResponse;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.OperationType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.CandidateCVRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateCVService extends AbstractCrudService<CandidateCV, CandidateCVResponse> {

    private static final Path CV_UPLOAD_DIRECTORY = Paths.get("temp", "cv-uploads");
    private static final Set<String> STOP_WORDS = Set.of(
            "and", "the", "for", "with", "that", "this", "from", "have", "has", "you",
            "your", "are", "was", "were", "will", "about", "into", "their", "there", "they",
            "them", "than", "then", "over", "under", "while", "where", "when", "what", "which"
    );

    private final CandidateCVRepository candidateCVRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    @Value("${app.async-processing.enabled:true}")
    private boolean asyncProcessingEnabled;

    @Override
    protected JpaRepository<CandidateCV, UUID> repository() {
        return candidateCVRepository;
    }


    @Override
    protected CandidateCVResponse toResponse(CandidateCV entity) {
        return new CandidateCVResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getOriginalFileUrl(),
                entity.getOriginalFileName(),
                entity.getFileFormat(),
                entity.getParsedContent(),
                entity.getParseStatus(),
                entity.getParseErrorMessage(),
                entity.getAtsScore(),
                entity.getIsActive(),
                entity.getUploadedAt(),
                entity.getUpdatedAt()
        );
    }

    @Override
    protected String resourceName() {
        return "CandidateCV";
    }

    public CandidateCVResponse uploadAndParseCv(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded CV file is empty");
        }

        deactivateExistingActiveCv(userId);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        FileFormat format = resolveFileFormat(originalFilename);
        String storedPath = persistFile(userId, originalFilename, file);

        CandidateCV candidateCV = new CandidateCV();
        candidateCV.setUserId(userId);
        candidateCV.setOriginalFileName(originalFilename);
        candidateCV.setOriginalFileUrl(storedPath);
        candidateCV.setFileFormat(format);
        candidateCV.setParseStatus(ProcessingStatus.PENDING);
        candidateCV.setIsActive(Boolean.TRUE);
        candidateCV.setUploadedAt(Instant.now());
        candidateCV.setUpdatedAt(Instant.now());

        CandidateCV saved = candidateCVRepository.saveAndFlush(candidateCV);
        CandidateCVResponse createdResponse = toResponse(saved);
        if (asyncProcessingEnabled) {
            CompletableFuture.runAsync(() -> parseCvContent(saved.getId()));
        } else {
            parseCvContent(saved.getId());
        }
        return createdResponse;
    }

    @Transactional
    public void parseCvContent(UUID cvId) {
        long startedAt = System.currentTimeMillis();
        CandidateCV candidateCV = requireEntity(cvId);
        candidateCV.setParseStatus(ProcessingStatus.IN_PROGRESS);
        candidateCV.setParseErrorMessage(null);
        preserveCurrentActiveFlag(candidateCV);
        candidateCVRepository.saveAndFlush(candidateCV);

        try {
            String rawText = tika.parseToString(Path.of(candidateCV.getOriginalFileUrl()));
            Map<String, Object> structured = buildStructuredCvPayload(rawText);
            candidateCV.setParsedContent(objectMapper.writeValueAsString(structured));
            candidateCV.setParseStatus(ProcessingStatus.COMPLETED);
            candidateCV.setParseErrorMessage(null);
            preserveCurrentActiveFlag(candidateCV);
            candidateCVRepository.saveAndFlush(candidateCV);
            auditLogService.logOperation(
                    candidateCV.getUserId(),
                    OperationType.CV_PARSE,
                    "CandidateCV",
                    candidateCV.getId(),
                    ProcessingStatus.COMPLETED,
                    1200,
                    (int) (System.currentTimeMillis() - startedAt),
                    null
            );
            computeAtsScore(cvId);
        } catch (Exception e) {
            log.warn("Failed to parse CV {}: {}", cvId, e.getMessage());
            candidateCV.setParseStatus(ProcessingStatus.FAILED);
            candidateCV.setParseErrorMessage(truncate(e.getMessage(), 500));
            preserveCurrentActiveFlag(candidateCV);
            candidateCVRepository.saveAndFlush(candidateCV);
            auditLogService.logOperation(
                    candidateCV.getUserId(),
                    OperationType.CV_PARSE,
                    "CandidateCV",
                    candidateCV.getId(),
                    ProcessingStatus.FAILED,
                    200,
                    (int) (System.currentTimeMillis() - startedAt),
                    e.getMessage()
            );
        }
    }

    @Transactional
    public int computeAtsScore(UUID cvId) {
        long startedAt = System.currentTimeMillis();
        CandidateCV candidateCV = requireEntity(cvId);
        if (candidateCV.getParsedContent() == null || candidateCV.getParsedContent().isBlank()) {
            throw new ResourceNotFoundException("CV parsed content is not available for cvId=" + cvId);
        }

        try {
            JsonNode root = objectMapper.readTree(candidateCV.getParsedContent());
            String rawText = root.path("rawText").asText("");
            List<String> keywords = new ArrayList<>();
            root.path("keywords").forEach(node -> keywords.add(node.asText()));

            String normalizedText = rawText.toLowerCase(Locale.ROOT);
            long matchedKeywords = keywords.stream()
                    .filter(k -> normalizedText.contains(k.toLowerCase(Locale.ROOT)))
                    .count();

            int keywordScore = keywords.isEmpty() ? 50 : (int) Math.round((matchedKeywords * 100.0) / keywords.size());
            int sectionsScore = scoreSectionPresence(normalizedText);
            int lengthScore = scoreTextLength(rawText);

            int atsScore = Math.min(100, Math.max(0,
                    (int) Math.round((keywordScore * 0.55) + (sectionsScore * 0.30) + (lengthScore * 0.15))
            ));

            candidateCV.setAtsScore(atsScore);
                preserveCurrentActiveFlag(candidateCV);
            candidateCVRepository.saveAndFlush(candidateCV);
            auditLogService.logOperation(
                    candidateCV.getUserId(),
                    OperationType.CV_SCORE,
                    "CandidateCV",
                    candidateCV.getId(),
                    ProcessingStatus.COMPLETED,
                    300,
                    (int) (System.currentTimeMillis() - startedAt),
                    null
            );
            return atsScore;
        } catch (IOException e) {
            auditLogService.logOperation(
                    candidateCV.getUserId(),
                    OperationType.CV_SCORE,
                    "CandidateCV",
                    candidateCV.getId(),
                    ProcessingStatus.FAILED,
                    50,
                    (int) (System.currentTimeMillis() - startedAt),
                    e.getMessage()
            );
            throw new IllegalStateException("Unable to compute ATS score for cvId=" + cvId, e);
        }
    }

    public CandidateCVResponse getActiveCvForUser(UUID userId) {
        return candidateCVRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .max(Comparator.comparing(CandidateCV::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active CV found for userId=" + userId));
    }

    public List<CandidateCVResponse> getAllCvsForUser(UUID userId) {
        return candidateCVRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(CandidateCV::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deactivateCv(UUID cvId, UUID userId) {
        CandidateCV candidateCV = candidateCVRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CandidateCV not found with id=" + cvId + " for userId=" + userId));
        candidateCV.setIsActive(Boolean.FALSE);
        candidateCVRepository.saveAndFlush(candidateCV);
    }

    public CandidateCVResponse getCvById(UUID cvId, UUID userId) {
        return candidateCVRepository.findByIdAndUserId(cvId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("CandidateCV not found with id=" + cvId + " for userId=" + userId));
    }

    public List<CandidateCVResponse> findByUserId(UUID userId) {
        return candidateCVRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CandidateCVResponse create(CandidateCVRequest request) {
        CandidateCV entity = new CandidateCV();
        apply(entity, request);
        return toResponse(candidateCVRepository.save(entity));
    }

    @Transactional
    public CandidateCVResponse update(UUID id, CandidateCVRequest request) {
        CandidateCV entity = requireEntity(id);
        apply(entity, request);
        return toResponse(candidateCVRepository.save(entity));
    }

    private void apply(CandidateCV entity, CandidateCVRequest request) {
        entity.setUserId(request.userId());
        entity.setOriginalFileUrl(request.originalFileUrl().trim());
        entity.setOriginalFileName(request.originalFileName().trim());
        entity.setFileFormat(request.fileFormat());
        entity.setParsedContent(trimToNull(request.parsedContent()));
        entity.setParseStatus(request.parseStatus() != null ? request.parseStatus() : ProcessingStatus.PENDING);
        entity.setParseErrorMessage(trimToNull(request.parseErrorMessage()));
        entity.setAtsScore(request.atsScore());
        entity.setIsActive(request.isActive() != null ? request.isActive() : Boolean.TRUE);
        entity.setUploadedAt(request.uploadedAt());
        entity.setUpdatedAt(request.updatedAt());
    }

    private void deactivateExistingActiveCv(UUID userId) {
        List<CandidateCV> existingCvs = candidateCVRepository.findByUserId(userId);
        if (existingCvs.isEmpty()) {
            return;
        }
        existingCvs.forEach(cv -> cv.setIsActive(Boolean.FALSE));
        candidateCVRepository.saveAllAndFlush(existingCvs);
    }

    private FileFormat resolveFileFormat(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return FileFormat.DOCX;
        }
        return FileFormat.PDF;
    }

    private String sanitizeFilename(String originalFilename) {
        String fallback = "cv-upload.pdf";
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String persistFile(UUID userId, String filename, MultipartFile file) {
        try {
            Files.createDirectories(CV_UPLOAD_DIRECTORY);
            String storedName = userId + "_" + System.currentTimeMillis() + "_" + filename;
            Path storedPath = CV_UPLOAD_DIRECTORY.resolve(storedName).normalize();
            Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);
            return storedPath.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store CV file", e);
        }
    }

    private Map<String, Object> buildStructuredCvPayload(String rawText) {
        String safeText = Objects.requireNonNullElse(rawText, "");
        List<String> keywords = extractTopKeywords(safeText, 20);
        Map<String, Object> sections = new LinkedHashMap<>();
        String normalized = safeText.toLowerCase(Locale.ROOT);
        sections.put("education", normalized.contains("education"));
        sections.put("experience", normalized.contains("experience"));
        sections.put("skills", normalized.contains("skills"));
        sections.put("projects", normalized.contains("projects"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rawText", safeText);
        payload.put("keywords", keywords);
        payload.put("wordCount", safeText.isBlank() ? 0 : safeText.split("\\s+").length);
        payload.put("sections", sections);
        return payload;
    }

    private List<String> extractTopKeywords(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Map<String, Long> frequency = java.util.Arrays.stream(text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9\\s]", " ")
                        .split("\\s+"))
                .filter(token -> token.length() >= 4)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()));

        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int scoreSectionPresence(String normalizedText) {
        int score = 0;
        if (normalizedText.contains("experience")) {
            score += 30;
        }
        if (normalizedText.contains("skills")) {
            score += 30;
        }
        if (normalizedText.contains("education")) {
            score += 25;
        }
        if (normalizedText.contains("projects") || normalizedText.contains("certification")) {
            score += 15;
        }
        return Math.min(score, 100);
    }

    private int scoreTextLength(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return 0;
        }
        int wordCount = rawText.getBytes(StandardCharsets.UTF_8).length / 6;
        if (wordCount < 150) {
            return 45;
        }
        if (wordCount <= 900) {
            return 100;
        }
        return 70;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void preserveCurrentActiveFlag(CandidateCV candidateCV) {
        candidateCVRepository.findById(candidateCV.getId())
                .map(CandidateCV::getIsActive)
                .ifPresent(candidateCV::setIsActive);
    }
}

