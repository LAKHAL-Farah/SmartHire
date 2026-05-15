package tn.esprit.msroadmap.Services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.DTO.request.CreateCareerPathDto;
import tn.esprit.msroadmap.DTO.response.CareerPathTemplateDto;
import tn.esprit.msroadmap.Entities.CareerPathTemplate;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.CareerPathTemplateRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CareerPathTemplateService {

    private final CareerPathTemplateRepository repository;

    public CareerPathTemplateDto createTemplate(CreateCareerPathDto dto) {
        validateCreateOrUpdate(dto);

        CareerPathTemplate template = CareerPathTemplate.builder()
                .title(dto.getTitle().trim())
                .description(safeTrim(dto.getDescription()))
                .defaultTopics(safeTrim(dto.getDefaultTopics()))
                .difficulty(safeTrim(dto.getDifficulty()))
                .estimatedWeeks(dto.getEstimatedWeeks())
                .isPublished(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toDto(repository.save(template));
    }

    public CareerPathTemplateDto updateTemplate(Long id, CreateCareerPathDto dto) {
        validatePositiveId(id);
        validateCreateOrUpdate(dto);

        CareerPathTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career path template not found: " + id));

        template.setTitle(dto.getTitle().trim());
        template.setDescription(safeTrim(dto.getDescription()));
        template.setDefaultTopics(safeTrim(dto.getDefaultTopics()));
        template.setDifficulty(safeTrim(dto.getDifficulty()));
        template.setEstimatedWeeks(dto.getEstimatedWeeks());
        template.setUpdatedAt(LocalDateTime.now());

        return toDto(repository.save(template));
    }

    public void deleteTemplate(Long id) {
        validatePositiveId(id);
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Career path template not found: " + id);
        }
        repository.deleteById(id);
    }

    public List<CareerPathTemplateDto> getAllTemplates() {
        return repository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<CareerPathTemplateDto> getPublishedTemplates() {
        return repository.findByIsPublishedTrueOrderByTitleAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CareerPathTemplateDto getTemplateById(Long id) {
        validatePositiveId(id);
        CareerPathTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career path template not found: " + id));
        return toDto(template);
    }

    public CareerPathTemplateDto publishTemplate(Long id) {
        validatePositiveId(id);
        CareerPathTemplate template = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Career path template not found: " + id));

        template.setPublished(true);
        template.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(template));
    }

    private CareerPathTemplateDto toDto(CareerPathTemplate template) {
        return CareerPathTemplateDto.builder()
                .id(template.getId())
                .title(template.getTitle())
                .description(template.getDescription())
                .defaultTopics(template.getDefaultTopics())
                .difficulty(template.getDifficulty())
                .estimatedWeeks(template.getEstimatedWeeks())
                .isPublished(template.isPublished())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private void validateCreateOrUpdate(CreateCareerPathDto dto) {
        if (dto == null) {
            throw new BusinessException("Request body is required");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException("title is required");
        }
        if (dto.getEstimatedWeeks() != null && dto.getEstimatedWeeks() <= 0) {
            throw new BusinessException("estimatedWeeks must be greater than 0");
        }
    }

    private void validatePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("id must be a positive number");
        }
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
