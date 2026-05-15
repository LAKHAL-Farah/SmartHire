package tn.esprit.msassessment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.entity.Question;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.exception.BusinessException;
import tn.esprit.msassessment.repository.QuestionRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Picks random active questions whose {@link tn.esprit.msassessment.entity.Question#getTopic()} or
 * category code/title matches a keyword (e.g. "java", "sql").
 */
@Service
@RequiredArgsConstructor
public class TopicQuestionQueryService {

    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<Question> pickRandomQuestionsWithChoices(String topicKeyword, int requestedCount) {
        if (requestedCount < 1 || requestedCount > 50) {
            throw new BusinessException("Count must be between 1 and 50.");
        }
        String k = topicKeyword.toLowerCase(Locale.ROOT).trim();
        if (k.isEmpty()) {
            throw new BusinessException("Topic keyword is required.");
        }

        List<Question> candidates = questionRepository.findAllActiveWithCategoryForTopicFilter();
        List<Question> matched = candidates.stream()
                .filter(q -> matchesTopic(q, k))
                .collect(Collectors.toCollection(ArrayList::new));

        if (matched.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(matched);
        int n = Math.min(requestedCount, matched.size());
        List<Long> ids = matched.stream().limit(n).map(Question::getId).toList();
        return loadQuestionsInOrder(ids);
    }

    private static boolean matchesTopic(Question q, String k) {
        if (q.getTopic() != null && !q.getTopic().isBlank()) {
            String t = q.getTopic().toLowerCase(Locale.ROOT);
            if (t.contains(k) || k.contains(t)) {
                return true;
            }
        }
        QuestionCategory c = q.getCategory();
        if (c.getCode() != null && c.getCode().toLowerCase(Locale.ROOT).contains(k)) {
            return true;
        }
        return c.getTitle() != null && c.getTitle().toLowerCase(Locale.ROOT).contains(k);
    }

    private List<Question> loadQuestionsInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Question> loaded = questionRepository.findByIdInWithChoices(ids);
        Map<Long, Question> map = loaded.stream().collect(Collectors.toMap(Question::getId, q -> q));
        return ids.stream().map(map::get).filter(Objects::nonNull).toList();
    }
}
