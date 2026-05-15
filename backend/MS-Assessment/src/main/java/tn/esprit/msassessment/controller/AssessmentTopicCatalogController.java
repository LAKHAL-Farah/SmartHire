package tn.esprit.msassessment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msassessment.dto.response.RandomQuestionsResponse;
import tn.esprit.msassessment.mapper.AssessmentPaperMapper;
import tn.esprit.msassessment.service.TopicQuestionQueryService;

@RestController
@RequestMapping("/api/v1/assessment/catalog")
@RequiredArgsConstructor
public class AssessmentTopicCatalogController {

    private final TopicQuestionQueryService topicQuestionQueryService;
    private final AssessmentPaperMapper paperMapper;

    /**
     * Preview up to {@code count} random active questions matching {@code topic} (tag or category text).
     * Does not start a session; use {@code POST /api/v1/assessment/sessions/by-topic} to attempt.
     */
    @GetMapping("/random-questions")
    public ResponseEntity<RandomQuestionsResponse> randomQuestions(
            @RequestParam String topic,
            @RequestParam(defaultValue = "10") int count) {
        var picked = topicQuestionQueryService.pickRandomQuestionsWithChoices(topic, count);
        var items = picked.stream().map(paperMapper::toPaperItem).toList();
        return ResponseEntity.ok(new RandomQuestionsResponse(
                topic.trim(),
                count,
                items.size(),
                items));
    }
}
