package tn.esprit.msroadmap.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tn.esprit.msroadmap.DTO.response.JobDto;
import tn.esprit.msroadmap.DTO.response.SavedJobDto;
import tn.esprit.msroadmap.Entities.SavedJob;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.SavedJobRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobService {

    private final WebClient webClient;
    private final SavedJobRepository savedJobRepository;

    public JobService(@Qualifier("genericWebClient") WebClient webClient,
                      SavedJobRepository savedJobRepository) {
        this.webClient = webClient;
        this.savedJobRepository = savedJobRepository;
    }

    public Page<JobDto> searchJobs(String keyword, String location, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("keyword is required");
        }
        if (page < 0) {
            throw new BusinessException("page must be >= 0");
        }
        if (size <= 0 || size > 100) {
            throw new BusinessException("size must be between 1 and 100");
        }

        return fetchJobsFromProvider(keyword, location, page, size);
    }

    public List<JobDto> getRecommendations(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId must be a positive number");
        }

        List<SavedJobDto> savedJobs = getSavedJobs(userId);
        return savedJobs.stream()
            .map(SavedJobDto::getJob)
            .filter(job -> job != null)
            .limit(10)
            .collect(Collectors.toList());
    }

    public JobDto getJobById(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("id is required");
        }
        return fetchJobByIdFromProvider(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
    }

    public void saveJobForUser(Long userId, String jobId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId must be a positive number");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException("jobId is required");
        }

        if (savedJobRepository.existsByUserIdAndJobId(userId, jobId)) {
            return;
        }

        SavedJob saved = SavedJob.builder()
                .userId(userId)
                .jobId(jobId)
                .savedAt(LocalDateTime.now())
                .build();
        savedJobRepository.save(saved);
    }

    public List<SavedJobDto> getSavedJobs(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId must be a positive number");
        }

        return savedJobRepository.findByUserId(userId).stream()
                .map(this::toSavedJobDto)
                .collect(Collectors.toList());
    }

    public void removeSavedJob(Long savedJobId) {
        if (savedJobId == null || savedJobId <= 0) {
            throw new BusinessException("savedJobId must be a positive number");
        }
        if (!savedJobRepository.existsById(savedJobId)) {
            throw new ResourceNotFoundException("Saved job not found: " + savedJobId);
        }
        savedJobRepository.deleteById(savedJobId);
    }

    private Page<JobDto> fetchJobsFromProvider(String keyword, String location, int page, int size) {
        try {
            Map<?, ?> response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("remotive.com")
                                .path("/api/remote-jobs")
                                .queryParam("search", keyword == null ? "" : keyword.trim());
                        if (location != null && !location.isBlank()) {
                            builder = builder.queryParam("location", location.trim());
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15))
                    .onErrorReturn(Map.of())
                    .block();

            if (response == null || !(response.get("jobs") instanceof List<?> jobs)) {
                return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
            }

            List<JobDto> mapped = jobs.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> toJobDto((Map<?, ?>) item))
                    .collect(Collectors.toList());

            if (location != null && !location.isBlank()) {
                String normalizedLocation = location.trim().toLowerCase(Locale.ROOT);
                mapped = mapped.stream()
                        .filter(job -> job.getLocation() != null
                                && job.getLocation().toLowerCase(Locale.ROOT).contains(normalizedLocation))
                        .collect(Collectors.toList());
            }

            int start = Math.min(page * size, mapped.size());
            int end = Math.min(start + size, mapped.size());
            List<JobDto> content = start >= end ? List.of() : mapped.subList(start, end);
            return new PageImpl<>(content, PageRequest.of(page, size), mapped.size());
        } catch (Exception ex) {
            log.warn("Job provider lookup failed: {}", ex.getMessage());
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }
    }

    private Optional<JobDto> fetchJobByIdFromProvider(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        Page<JobDto> page = fetchJobsFromProvider(id, null, 0, 50);
        return page.getContent().stream()
                .filter(job -> id.equals(job.getId()))
                .findFirst();
    }

    private JobDto toJobDto(Map<?, ?> job) {
        String title = asString(job.get("title"));
        String company = asString(job.get("company_name"));
        String location = asString(job.get("candidate_required_location"));
        String description = asString(job.get("description"));
        String salary = asString(job.get("salary"));
        String url = asString(job.get("url"));
        String postedAt = asString(job.get("publication_date"));

        List<String> skills = new ArrayList<>();
        Object tags = job.get("tags");
        if (tags instanceof List<?> list) {
            skills = list.stream().map(String::valueOf).collect(Collectors.toList());
        }

        return JobDto.builder()
                .id(asString(job.get("id")))
                .title(title)
                .company(company)
                .location(location)
                .description(description)
                .salary(salary)
                .url(url)
                .postedAt(postedAt)
                .skills(skills)
                .build();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private SavedJobDto toSavedJobDto(SavedJob savedJob) {
        return SavedJobDto.builder()
                .id(savedJob.getId())
                .userId(savedJob.getUserId())
                .jobId(savedJob.getJobId())
                .savedAt(savedJob.getSavedAt())
                .job(fetchJobByIdFromProvider(savedJob.getJobId()).orElse(null))
                .build();
    }
}
