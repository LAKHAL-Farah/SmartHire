package tn.esprit.msassessment.dto.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to generate questions for a category using Ollama.
 */
public record GenerateQuestionsRequest(
    @NotNull(message = "Category ID is required")
    Long categoryId,
    
    @Min(value = 1, message = "Must generate at least 1 question")
    int count
) {}
