package tn.esprit.msassessment.dto.response;

import java.util.List;

public record QuestionPaperResponse(
        Long sessionId,
        Long categoryId,
        String categoryTitle,
        List<QuestionPaperItem> questions
) {}
