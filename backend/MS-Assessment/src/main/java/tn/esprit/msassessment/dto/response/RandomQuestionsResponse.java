package tn.esprit.msassessment.dto.response;

import java.util.List;

public record RandomQuestionsResponse(
        String topic,
        int requestedCount,
        int actualCount,
        List<QuestionPaperItem> questions
) {}
