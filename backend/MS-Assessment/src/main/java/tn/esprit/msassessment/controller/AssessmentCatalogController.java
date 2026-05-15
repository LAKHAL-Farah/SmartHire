package tn.esprit.msassessment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msassessment.dto.response.CategoryResponse;
import tn.esprit.msassessment.service.AssessmentCatalogService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assessment/categories")
@RequiredArgsConstructor
public class AssessmentCatalogController {

    private final AssessmentCatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(catalogService.listCategories());
    }
}
