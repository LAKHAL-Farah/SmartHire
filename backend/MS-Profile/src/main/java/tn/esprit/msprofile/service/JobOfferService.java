package tn.esprit.msprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.JobOfferRequest;
import tn.esprit.msprofile.dto.response.JobOfferResponse;
import tn.esprit.msprofile.entity.CVVersion;
import tn.esprit.msprofile.entity.JobOffer;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.repository.CVVersionRepository;
import tn.esprit.msprofile.repository.JobOfferRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service("legacyJobOfferService")
@RequiredArgsConstructor
@Slf4j
public class JobOfferService extends AbstractCrudService<JobOffer, JobOfferResponse> {

    private final JobOfferRepository jobOfferRepository;
    private final CVVersionRepository cvVersionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.async-processing.enabled:true}")
    private boolean asyncProcessingEnabled;

    @Override
    protected JpaRepository<JobOffer, UUID> repository() {
        return jobOfferRepository;
    }

    @Override
    protected JobOfferResponse toResponse(JobOffer entity) {
        return new JobOfferResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getCompany(),
                entity.getRawDescription(),
                entity.getExtractedKeywords(),
                entity.getSourceUrl(),
                entity.getCreatedAt()
        );
    }

    @Override
    protected String resourceName() {
        return "JobOffer";
    }

    @Transactional
    public JobOfferResponse createJobOffer(UUID userId, JobOfferRequest request) {
        JobOffer entity = new JobOffer();
        apply(entity, request);
        entity.setUserId(userId);
        entity.setExtractedKeywords(null);
        entity.setCreatedAt(request.createdAt() != null ? request.createdAt() : Instant.now());

        JobOffer saved = jobOfferRepository.save(entity);
        if (asyncProcessingEnabled) {
            CompletableFuture.runAsync(() -> {
                try {
                    extractKeywords(saved.getId());
                } catch (Exception e) {
                    log.warn("Keyword extraction failed for job offer {}: {}", saved.getId(), e.getMessage());
                }
            });
        } else {
            extractKeywords(saved.getId());
        }

        return toResponse(saved);
    }

    @Transactional
    public List<String> extractKeywords(UUID jobOfferId) {
        JobOffer offer = requireEntity(jobOfferId);
        List<String> keywords = extractKeywordList(offer.getRawDescription());
        offer.setExtractedKeywords(toJsonArray(keywords));
        jobOfferRepository.save(offer);
        return keywords;
    }

    public List<JobOfferResponse> getJobOffersForUser(UUID userId) {
        return jobOfferRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public JobOfferResponse getJobOfferById(UUID jobOfferId, UUID userId) {
        return jobOfferRepository.findByIdAndUserId(jobOfferId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("JobOffer not found with id=" + jobOfferId + " for userId=" + userId));
    }

    @Transactional
    public void deleteJobOffer(UUID jobOfferId, UUID userId) {
        JobOffer offer = jobOfferRepository.findByIdAndUserId(jobOfferId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("JobOffer not found with id=" + jobOfferId + " for userId=" + userId));

        List<CVVersion> versions = cvVersionRepository.findByJobOfferId(jobOfferId);
        if (!versions.isEmpty()) {
            cvVersionRepository.deleteAll(versions);
        }
        jobOfferRepository.delete(offer);
    }

    public List<JobOfferResponse> findByUserId(UUID userId) {
        return jobOfferRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public JobOfferResponse create(JobOfferRequest request) {
        return createJobOffer(request.userId(), request);
    }

    @Transactional
    public JobOfferResponse update(UUID id, JobOfferRequest request) {
        JobOffer entity = requireEntity(id);
        apply(entity, request);
        return toResponse(jobOfferRepository.save(entity));
    }

    private void apply(JobOffer entity, JobOfferRequest request) {
        entity.setUserId(request.userId());
        entity.setTitle(request.title().trim());
        entity.setCompany(trimToNull(request.company()));
        entity.setRawDescription(request.rawDescription().trim());
        entity.setExtractedKeywords(trimToNull(request.extractedKeywords()));
        entity.setSourceUrl(trimToNull(request.sourceUrl()));
        entity.setCreatedAt(request.createdAt());
    }

    private List<String> extractKeywordList(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }

        Set<String> stopWords = Set.of(
                "with", "that", "this", "from", "have", "will", "your", "team", "ability", "experience", "years"
        );

        return Arrays.stream(description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 4)
                .filter(token -> !stopWords.contains(token))
                .collect(Collectors.groupingBy(token -> token, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(20)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String toJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize extracted keywords", e);
        }
    }
}

