package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.service.InterviewQuestionService;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InterviewQuestionServiceImpl implements InterviewQuestionService {

    private final InterviewQuestionRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<InterviewQuestion> getAllActive() {
        log.debug("Fetching all active questions");
        return repository.findByIsActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewQuestion getById(Long id) {
        log.debug("Fetching question by id: {}", id);
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview question not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewQuestion> getByCareerPath(Long careerPathId) {
        log.debug("Fetching questions for careerPathId: {}", careerPathId);
        return repository.findByCareerPathIdAndIsActiveTrue(careerPathId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewQuestion> filterForAdaptiveEngine(RoleType role,
                                                           QuestionType type,
                                                           DifficultyLevel difficulty,
                                                           List<Long> excludeIds) {
        log.debug("Filtering questions for adaptive engine - role: {}, type: {}, difficulty: {}", role, type, difficulty);
        
        // Build role list - include provided role and ALL role
        List<RoleType> roles = new java.util.ArrayList<>();
        if (role != null) {
            roles.add(role);
        }
        roles.add(RoleType.ALL);
        
        // Use dynamic filtering based on provided parameters
        try {
            if (type != null && difficulty != null) {
                return repository.findExcludingIds(excludeIds, roles, type, difficulty);
            } else if (type != null) {
                return repository.findByRoleTypeInAndIsActiveTrue(roles).stream()
                        .filter(q -> q.getType() == type)
                        .filter(q -> !excludeIds.contains(q.getId()))
                        .collect(java.util.stream.Collectors.toList());
            } else if (difficulty != null) {
                return repository.findByRoleTypeInAndIsActiveTrue(roles).stream()
                        .filter(q -> q.getDifficulty() == difficulty)
                        .filter(q -> !excludeIds.contains(q.getId()))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                return repository.findByRoleTypeInAndIsActiveTrue(roles).stream()
                        .filter(q -> !excludeIds.contains(q.getId()))
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error filtering questions", e);
            return repository.findByIsActiveTrue().stream()
                    .filter(q -> !excludeIds.contains(q.getId()))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    @Override
    public InterviewQuestion createQuestion(InterviewQuestion question) {
        log.debug("Creating new question with text: {}", question.getQuestionText().substring(0, Math.min(50, question.getQuestionText().length())));

        // Ensure the question is marked as active
        if (!question.isActive()) {
            question.setActive(true);
        }

        InterviewQuestion saved = repository.save(question);
        log.info("Question created with id: {}", saved.getId());
        return saved;
    }

    @Override
    public InterviewQuestion updateQuestion(Long id, InterviewQuestion updated) {
        log.debug("Updating question with id: {}", id);

        InterviewQuestion question = getById(id);

        if (updated.getQuestionText() != null) {
            question.setQuestionText(updated.getQuestionText());
        }
        if (updated.getDifficulty() != null) {
            question.setDifficulty(updated.getDifficulty());
        }
        if (updated.getType() != null) {
            question.setType(updated.getType());
        }
        if (updated.getDomain() != null) {
            question.setDomain(updated.getDomain());
        }
        if (updated.getCategory() != null) {
            question.setCategory(updated.getCategory());
        }

        InterviewQuestion saved = repository.save(question);
        log.info("Question updated with id: {}", id);
        return saved;
    }

    @Override
    public void softDeleteQuestion(Long id) {
        log.debug("Soft deleting question: {}", id);

        InterviewQuestion question = getById(id);
        question.setActive(false);
        repository.save(question);

        log.info("Question soft deleted with id: {}", id);
    }

    @Override
    public InterviewQuestion addTag(Long questionId, String tag) {
        log.debug("Adding tag '{}' to question: {}", tag, questionId);

        InterviewQuestion question = getById(questionId);

        try {
            List<String> tags = new ArrayList<>();
            if (question.getTags() != null && !question.getTags().isBlank()) {
                try {
                    tags = objectMapper.readValue(
                            question.getTags(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                    );
                } catch (Exception ignored) {
                    Map<String, Object> tagMap = objectMapper.readValue(question.getTags(), Map.class);
                    tags = new ArrayList<>(tagMap.keySet());
                }
            }

            if (!tags.contains(tag)) {
                tags.add(tag);
            }
            question.setTags(objectMapper.writeValueAsString(tags));

            InterviewQuestion updated = repository.save(question);
            log.info("Tag added to question: {}", questionId);
            return updated;
        } catch (Exception ex) {
            log.error("Error adding tag to question", ex);
            throw new RuntimeException("Failed to add tag to question", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> checkBankCoverage() {
        log.debug("Checking question bank coverage");

        Map<String, Long> coverage = new HashMap<>();

        // Count per role + type combination
        for (RoleType role : getRolesToCheck()) {
            for (QuestionType type : QuestionType.values()) {
                String key = role + "_" + type;
                long count = repository.countByRoleTypeAndType(role, type);
                coverage.put(key, count);
                if (count < 10) {
                    log.warn("Low question coverage for {}: only {} questions", key, count);
                }
            }
        }

        return coverage;
    }

    private List<RoleType> getRolesToCheck() {
        return List.of(RoleType.SE, RoleType.CLOUD, RoleType.AI);
    }
}
