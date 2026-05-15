package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msroadmap.DTO.response.CareerPathTemplateDto;
import tn.esprit.msroadmap.Services.CareerPathTemplateService;

import java.util.List;

@RestController
@RequestMapping("/api/career-paths")
@RequiredArgsConstructor
public class CareerPathController {

    private final CareerPathTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<CareerPathTemplateDto>> getPublishedCareerPaths() {
        return ResponseEntity.ok(templateService.getPublishedTemplates());
    }
}
