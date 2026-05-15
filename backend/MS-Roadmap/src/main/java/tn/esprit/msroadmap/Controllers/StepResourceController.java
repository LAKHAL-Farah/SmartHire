package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.StepResourceDto;
import tn.esprit.msroadmap.Entities.StepResource;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Mapper.StepResourceMapper;
import tn.esprit.msroadmap.ServicesImpl.IStepResourceService;

import java.util.List;

@RestController
@RequestMapping("/api/step-resources")
@RequiredArgsConstructor
public class StepResourceController {

    private final IStepResourceService stepResourceService;
    private final StepResourceMapper stepResourceMapper;

    @GetMapping("/search")
    public ResponseEntity<List<StepResourceDto>> search(
            @RequestParam String topic,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String type) {
        if (topic == null || topic.isBlank()) {
            throw new BusinessException("topic is required");
        }
        List<StepResource> results = stepResourceService.searchResources(topic, provider, type);
        return ResponseEntity.ok(stepResourceMapper.toDtoList(results));
    }

    @GetMapping("/step/{stepId}")
    public ResponseEntity<List<StepResourceDto>> getByStep(@PathVariable Long stepId) {
        if (stepId == null || stepId <= 0) {
            throw new BusinessException("stepId must be a positive number");
        }
        List<StepResource> resources = stepResourceService.getResourcesByStepId(stepId);
        return ResponseEntity.ok(stepResourceMapper.toDtoList(resources));
    }

    @PostMapping("/step/{stepId}")
    public ResponseEntity<StepResourceDto> addToStep(
            @PathVariable Long stepId,
            @RequestBody tn.esprit.msroadmap.DTO.request.StepResourceDto request
    ) {
        if (stepId == null || stepId <= 0) {
            throw new BusinessException("stepId must be a positive number");
        }
        StepResource created = stepResourceService.addResourceToStep(stepId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(stepResourceMapper.toDto(created));
    }

    @PostMapping("/step/{stepId}/sync")
    public ResponseEntity<Void> syncResources(@PathVariable Long stepId) {
        if (stepId == null || stepId <= 0) {
            throw new BusinessException("stepId must be a positive number");
        }
        stepResourceService.syncResourcesForStep(stepId);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{resourceId}")
    public ResponseEntity<Void> delete(@PathVariable Long resourceId) {
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException("resourceId must be a positive number");
        }
        stepResourceService.deleteResource(resourceId);
        return ResponseEntity.noContent().build();
    }
}
