package tn.esprit.msassessment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.ai.OllamaClient;
import tn.esprit.msassessment.dto.ai.GeneratedQuestion;
import tn.esprit.msassessment.dto.ai.GeneratedQuestionDto;
import tn.esprit.msassessment.dto.ai.GenerateQuestionsResponse;
import tn.esprit.msassessment.entity.AnswerChoice;
import tn.esprit.msassessment.entity.Question;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.entity.enums.DifficultyLevel;
import tn.esprit.msassessment.exception.ResourceNotFoundException;
import tn.esprit.msassessment.repository.AnswerChoiceRepository;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;
import tn.esprit.msassessment.repository.QuestionRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to generate questions using Ollama and save them to the database.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class QuestionGenerationService {

    private final OllamaClient ollamaClient;
    private final QuestionCategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;
    private final AnswerChoiceRepository answerChoiceRepository;

    /**
     * Generate questions for a category and optionally save them.
     * Returns the generated questions for preview before saving.
     */
    public GenerateQuestionsResponse generateQuestionsPreview(Long categoryId, int count) {
        QuestionCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        List<GeneratedQuestion> generated = ollamaClient.generateQuestions(
            category.getTitle(),
            category.getDescription(),
            count
        );

        if (generated.isEmpty()) {
            return GenerateQuestionsResponse.failure(
                categoryId,
                "Failed to generate questions. Ensure Ollama is running and configured correctly."
            );
        }

        List<GeneratedQuestionDto> dtos = generated.stream()
                .map(GeneratedQuestionDto::from)
                .toList();

        return GenerateQuestionsResponse.success(categoryId, category.getTitle(), dtos);
    }

    /**
     * Save generated questions to the database.
     */
    public void saveGeneratedQuestions(Long categoryId, List<GeneratedQuestionDto> questions) {
        QuestionCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        for (GeneratedQuestionDto dto : questions) {
            // Create question
            Question question = Question.builder()
                    .category(category)
                    .prompt(dto.prompt())
                    .points(dto.points())
                    .difficulty(DifficultyLevel.valueOf(dto.difficulty()))
                    .active(true)
                    .build();

            question = questionRepository.save(question);

            // Create answer choices
            List<String> choices = dto.choices();
            for (int i = 0; i < choices.size(); i++) {
                AnswerChoice choice = AnswerChoice.builder()
                        .question(question)
                        .label(choices.get(i))
                        .correct(i == dto.correctIndex())
                        .sortOrder(i + 1)
                        .build();

                answerChoiceRepository.save(choice);
            }

            log.info("[Generation] Saved question: {} for category: {}", question.getId(), categoryId);
        }
    }

    /**
     * Check if Ollama is available.
     */
    public boolean isOllamaAvailable() {
        return ollamaClient.isAvailable();
    }
}
