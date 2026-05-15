package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.CreateCareerPathDto;
import tn.esprit.msroadmap.DTO.response.CareerPathTemplateDto;
import tn.esprit.msroadmap.Services.CareerPathTemplateService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/career-paths")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CareerPathAdminController {

    private final CareerPathTemplateService templateService;

    @PostMapping
    public ResponseEntity<CareerPathTemplateDto> create(@RequestBody CreateCareerPathDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CareerPathTemplateDto> update(
            @PathVariable Long id,
            @RequestBody CreateCareerPathDto request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CareerPathTemplateDto>> getAll() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CareerPathTemplateDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<CareerPathTemplateDto> publish(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.publishTemplate(id));
    }
}
