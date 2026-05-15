package tn.esprit.msassessment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msassessment.dto.ai.GenerateQuestionsRequest;
import tn.esprit.msassessment.dto.ai.GenerateQuestionsResponse;
import tn.esprit.msassessment.dto.ai.GeneratedQuestionDto;
import tn.esprit.msassessment.service.QuestionGenerationService;

import java.util.List;

/**
 * API endpoints for AI-powered question generation using Ollama.
 */
@RestController
@RequestMapping("/api/v1/assessment/admin/generate")
@RequiredArgsConstructor
public class QuestionGenerationController {

    private final QuestionGenerationService generationService;

    /**
     * Generate questions for a category (preview mode - not saved yet).
     * POST /api/v1/assessment/admin/generate/preview
     */
    @PostMapping("/preview")
    public ResponseEntity<GenerateQuestionsResponse> previewGeneratedQuestions(
            @Valid @RequestBody GenerateQuestionsRequest request) {
        GenerateQuestionsResponse response = generationService.generateQuestionsPreview(
            request.categoryId(),
            request.count()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Save previously generated questions to the database.
     * POST /api/v1/assessment/admin/generate/save
     */
    @PostMapping("/save")
    public ResponseEntity<Void> saveGeneratedQuestions(
            @RequestParam Long categoryId,
            @Valid @RequestBody List<GeneratedQuestionDto> questions) {
        generationService.saveGeneratedQuestions(categoryId, questions);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Check if Ollama service is available.
     * GET /api/v1/assessment/admin/generate/status
     */
    @GetMapping("/status")
    public ResponseEntity<OllamaStatusResponse> checkOllamaStatus() {
        boolean available = generationService.isOllamaAvailable();
        return ResponseEntity.ok(new OllamaStatusResponse(available));
    }

    public record OllamaStatusResponse(boolean available) {}
}
