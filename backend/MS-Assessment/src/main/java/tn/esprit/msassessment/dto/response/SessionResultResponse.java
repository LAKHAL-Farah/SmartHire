package tn.esprit.msassessment.dto.response;

import java.util.List;

public record SessionResultResponse(SessionResponse session, List<AnswerReviewItem> answers) {}
