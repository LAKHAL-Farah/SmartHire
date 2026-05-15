package tn.esprit.msassessment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.dto.admin.*;
import tn.esprit.msassessment.entity.AnswerChoice;
import tn.esprit.msassessment.entity.Question;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.entity.enums.DifficultyLevel;
import tn.esprit.msassessment.exception.BusinessException;
import tn.esprit.msassessment.exception.ResourceNotFoundException;
import tn.esprit.msassessment.repository.AnswerChoiceRepository;
import tn.esprit.msassessment.repository.QuestionCategoryRepository;
import tn.esprit.msassessment.repository.QuestionRepository;
import tn.esprit.msassessment.repository.SessionAnswerRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentAdminService {

    private final QuestionCategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;
    private final AnswerChoiceRepository answerChoiceRepository;
    private final SessionAnswerRepository sessionAnswerRepository;

    @Transactional(readOnly = true)
    public List<CategoryAdminResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> new CategoryAdminResponse(
                        c.getId(),
                        c.getCode(),
                        c.getTitle(),
                        c.getDescription(),
                        questionRepository.countByCategory_Id(c.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryAdminResponse getCategory(Long id) {
        QuestionCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        return new CategoryAdminResponse(
                c.getId(),
                c.getCode(),
                c.getTitle(),
                c.getDescription(),
                questionRepository.countByCategory_Id(c.getId())
        );
    }

    public CategoryAdminResponse createCategory(CategoryAdminRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.findByCode(code).isPresent()) {
            throw new BusinessException("Category code already exists: " + code);
        }
        QuestionCategory c = QuestionCategory.builder()
                .code(code)
                .title(request.title().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .build();
        c = categoryRepository.save(c);
        return new CategoryAdminResponse(c.getId(), c.getCode(), c.getTitle(), c.getDescription(), 0);
    }

    public CategoryAdminResponse updateCategory(Long id, CategoryAdminRequest request) {
        QuestionCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        categoryRepository.findByCode(code).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BusinessException("Category code already in use: " + code);
            }
        });
        c.setCode(code);
        c.setTitle(request.title().trim());
        c.setDescription(request.description() != null ? request.description().trim() : null);
        c = categoryRepository.save(c);
        return new CategoryAdminResponse(
                c.getId(),
                c.getCode(),
                c.getTitle(),
                c.getDescription(),
                questionRepository.countByCategory_Id(c.getId())
        );
    }

    public void deleteCategory(Long id) {
        QuestionCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        List<Question> questions = questionRepository.findByCategory_IdOrderByIdAsc(id);
        for (Question q : questions) {
            if (sessionAnswerRepository.existsByQuestion_Id(q.getId())) {
                throw new BusinessException(
                        "Cannot delete category: at least one question was already used in a candidate session.");
            }
        }
        categoryRepository.delete(c);
    }

    @Transactional(readOnly = true)
    public List<QuestionAdminResponse> listQuestions(Long categoryId) {
        ensureCategory(categoryId);
        return questionRepository.findByCategory_IdOrderByIdAsc(categoryId).stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    public QuestionAdminResponse createQuestion(Long categoryId, QuestionAdminRequest request) {
        QuestionCategory cat = ensureCategory(categoryId);
        DifficultyLevel diff = parseDifficulty(request.difficulty());
        Question q = Question.builder()
                .category(cat)
                .prompt(request.prompt().trim())
                .points(request.points())
                .difficulty(diff)
                .active(request.active())
                .topic(normalizeTopic(request.topic()))
                .build();
        q = questionRepository.save(q);
        return toQuestionResponse(q);
    }

    public QuestionAdminResponse updateQuestion(Long questionId, QuestionAdminRequest request) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        DifficultyLevel diff = parseDifficulty(request.difficulty());
        q.setPrompt(request.prompt().trim());
        q.setPoints(request.points());
        q.setDifficulty(diff);
        q.setActive(request.active());
        q.setTopic(normalizeTopic(request.topic()));
        return toQuestionResponse(questionRepository.save(q));
    }

    public void deleteQuestion(Long questionId) {
        if (sessionAnswerRepository.existsByQuestion_Id(questionId)) {
            throw new BusinessException("Cannot delete question: it appears in past candidate sessions.");
        }
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        questionRepository.delete(q);
    }

    public ChoiceAdminResponse createChoice(Long questionId, ChoiceAdminRequest request) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        AnswerChoice choice = AnswerChoice.builder()
                .question(q)
                .label(request.label().trim())
                .correct(request.correct())
                .sortOrder(request.sortOrder())
                .build();
        choice = answerChoiceRepository.save(choice);
        return toChoiceResponse(choice);
    }

    public ChoiceAdminResponse updateChoice(Long choiceId, ChoiceAdminRequest request) {
        AnswerChoice choice = answerChoiceRepository.findById(choiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Choice not found: " + choiceId));
        if (sessionAnswerRepository.existsBySelectedChoice_Id(choiceId)) {
            throw new BusinessException("Cannot edit choice: it was selected in a past session.");
        }
        choice.setLabel(request.label().trim());
        choice.setCorrect(request.correct());
        choice.setSortOrder(request.sortOrder());
        return toChoiceResponse(answerChoiceRepository.save(choice));
    }

    public void deleteChoice(Long choiceId) {
        if (sessionAnswerRepository.existsBySelectedChoice_Id(choiceId)) {
            throw new BusinessException("Cannot delete choice: it was selected in a past session.");
        }
        AnswerChoice choice = answerChoiceRepository.findById(choiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Choice not found: " + choiceId));
        answerChoiceRepository.delete(choice);
    }

    private static String normalizeTopic(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private QuestionCategory ensureCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    private static DifficultyLevel parseDifficulty(String raw) {
        try {
            return DifficultyLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException("Invalid difficulty. Use: EASY, MEDIUM, HARD");
        }
    }

    private QuestionAdminResponse toQuestionResponse(Question q) {
        List<AnswerChoice> sorted = q.getChoices().stream()
                .sorted(Comparator.comparing(AnswerChoice::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<ChoiceAdminResponse> choices = sorted.stream().map(this::toChoiceResponse).toList();
        return new QuestionAdminResponse(
                q.getId(),
                q.getCategory().getId(),
                q.getPrompt(),
                q.getPoints(),
                q.getDifficulty(),
                q.isActive(),
                q.getTopic(),
                choices
        );
    }

    private ChoiceAdminResponse toChoiceResponse(AnswerChoice c) {
        return new ChoiceAdminResponse(
                c.getId(),
                c.getLabel(),
                c.isCorrect(),
                c.getSortOrder() != null ? c.getSortOrder() : 0
        );
    }
}
