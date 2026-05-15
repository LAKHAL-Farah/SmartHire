package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tn.esprit.msinterview.ai.NvidiaAiClient;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.CodeExecutionResult;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.repository.CodeExecutionResultRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.EvaluationEngine;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class NvidiaEvaluationEngine implements EvaluationEngine {

    private final NvidiaAiClient aiClient;
    private final SessionAnswerRepository answerRepository;
    private final CodeExecutionResultRepository codeExecutionResultRepository;

    private static final int QUESTION_MAX_CHARS = 450;
    private static final int EXPECTED_POINTS_MAX_CHARS = 700;
    private static final int TRANSCRIPT_MAX_CHARS = 1200;
    private static final int CODE_MAX_CHARS = 2600;
    private static final int EXPLANATION_MAX_CHARS = 900;
    private static final int STATUS_MAX_CHARS = 120;

    @Override
    public AnswerEvaluation evaluateTextAnswer(SessionAnswer answer) {
        InterviewQuestion question = answer.getQuestion();

        String questionText = truncate(question != null ? question.getQuestionText() : "", QUESTION_MAX_CHARS);
        String expectedPoints = truncate(question != null ? question.getExpectedPoints() : "", EXPECTED_POINTS_MAX_CHARS);
        String questionType = question != null && question.getType() != null ? question.getType().name() : "GENERAL";
        String transcript = truncate(answer.getAnswerText() != null ? answer.getAnswerText() : "", TRANSCRIPT_MAX_CHARS);

        JsonNode result = callEvaluationPrompt(questionText, expectedPoints, transcript, questionType);

        double relevance = safeDouble(result, "relevance", 5.0);
        double clarity = safeDouble(result, "clarity", 5.0);
        double technical = safeDouble(result, "technical", 5.0);
        double communication = safeDouble(result, "communication", 5.0);

        AnswerEvaluation eval = AnswerEvaluation.builder()
                .answer(answer)
                .contentScore(round2(relevance))
                .clarityScore(round2(clarity))
                .technicalScore(round2(technical))
                .confidenceScore(round2(communication))
                .toneScore(round2(communication))
                .postureScore(round2(communication))
                .aiFeedback(nonBlank(result.path("aiFeedback").asText(null), "AI feedback unavailable."))
                .followUpGenerated(nonBlank(result.path("followUpQuestion").asText(null), "Can you expand on your solution trade-offs?"))
                .build();

        double overall = (avg(eval.getContentScore(), eval.getClarityScore()) * 0.30)
                + (orZero(eval.getTechnicalScore()) * 0.25)
                + (avg(eval.getConfidenceScore(), eval.getToneScore()) * 0.25)
                + (orZero(eval.getPostureScore()) * 0.20);

        eval.setOverallScore(round2(overall));
        return eval;
    }

        @Override
        public AnswerEvaluation evaluateCodingAnswer(Long answerId) {
                SessionAnswer answer = answerRepository.findById(answerId)
                                .orElseThrow(() -> new ResourceNotFoundException("Answer not found: " + answerId));

                InterviewQuestion question = answer.getQuestion();
                CodeExecutionResult execution = codeExecutionResultRepository.findLatest(answerId).orElse(null);

                String questionText = truncate(question != null ? question.getQuestionText() : "", QUESTION_MAX_CHARS);
                String expectedPoints = truncate(question != null ? question.getExpectedPoints() : "", EXPECTED_POINTS_MAX_CHARS);
                String language = answer.getCodeLanguage() != null ? answer.getCodeLanguage().name().toLowerCase() : "python";
                String sourceCode = truncate(nonBlank(answer.getCodeAnswer(), ""), CODE_MAX_CHARS);
                String explanation = truncate(nonBlank(answer.getAnswerText(), "No explanation provided."), EXPLANATION_MAX_CHARS);

                int passedCount = execution != null && execution.getTestCasesPassed() != null ? execution.getTestCasesPassed() : 0;
                int totalCount = execution != null && execution.getTestCasesTotal() != null ? execution.getTestCasesTotal() : 0;
                String statusDescription = truncate(execution != null ? nonBlank(execution.getStatusDescription(), "Unknown") : "Unknown", STATUS_MAX_CHARS);
                long timeMs = execution != null && execution.getExecutionTimeMs() != null ? execution.getExecutionTimeMs() : 0L;

                String systemPrompt = """
                        You are a strict technical interviewer.
                        Score quickly and consistently.
                        Return ONLY valid JSON.
                        """;

                String userPrompt = """
                        QUESTION: %s
                        EXPECTED_KEY_POINTS: %s
                        LANGUAGE: %s
                        TESTS_PASSED: %d
                        TESTS_TOTAL: %d
                        EXECUTION_STATUS: %s
                        EXECUTION_TIME_MS: %d

                        CODE:
                        %s

                        EXPLANATION:
                        %s

                        Rubric (0-10 numbers):
                        - codeCorrectness: mostly tied to tests (0/total -> near 0, full pass -> near 10)
                        - codeQuality: readability, structure, naming, maintainability; independent from test pass
                        - algorithmicThinking: approach fit, data structures, complexity awareness
                        - explanationClarity: concise and technically clear explanation
                        - depthOfKnowledge: depth shown in trade-offs and reasoning

                        Also return:
                        - aiFeedback: max 3 concise sentences, mention one strength and one improvement
                        - complexityAssessment: short form like "Time: O(n), Space: O(1)"
                        - followUpQuestion: one focused follow-up question

                        Return ONLY JSON:
                        {
                            "codeCorrectness": 7.0,
                            "codeQuality": 8.0,
                            "algorithmicThinking": 7.5,
                            "explanationClarity": 6.5,
                            "depthOfKnowledge": 7.0,
                            "aiFeedback": "...",
                            "complexityAssessment": "Time: O(n) Space: O(n)",
                            "followUpQuestion": "..."
                        }
                        """.formatted(
                                questionText,
                                expectedPoints,
                                language,
                                passedCount,
                                totalCount,
                                statusDescription,
                                timeMs,
                                sourceCode,
                                explanation
                );

                JsonNode result = aiClient.chatJson(systemPrompt, userPrompt);

                return AnswerEvaluation.builder()
                                .answer(answer)
                                .contentScore(round2(safeDouble(result, "depthOfKnowledge", 5.0)))
                                .clarityScore(round2(safeDouble(result, "explanationClarity", 5.0)))
                                .technicalScore(round2(safeDouble(result, "algorithmicThinking", 5.0)))
                                .codeCorrectnessScore(round2(safeDouble(result, "codeCorrectness", 5.0)))
                                .aiFeedback(nonBlank(result.path("aiFeedback").asText(null), "AI feedback unavailable."))
                                .followUpGenerated(nonBlank(result.path("followUpQuestion").asText(null), "Can you optimize your approach?"))
                                .codeComplexityNote(nonBlank(result.path("complexityAssessment").asText(null), "Time: Unknown Space: Unknown"))
                                .build();
        }

    private JsonNode callEvaluationPrompt(String questionText, String expectedPoints,
                                          String transcript, String questionType) {
        String systemPrompt = """
            You are a technical interviewer.
            Return concise and consistent scores.
            Return ONLY valid JSON.
            """;

        String userPrompt = """
            QUESTION: %s
            QUESTION TYPE: %s
            EXPECTED KEY POINTS: %s
            CANDIDATE ANSWER: %s

            Score each field 0-10 using this rubric:
            - relevance: alignment with question and expected points
            - clarity: structure, coherence, and precision
            - technical: correctness and depth
            - communication: confidence and professional articulation

            Also provide:
            - aiFeedback: max 2 concise sentences
            - followUpQuestion: one realistic follow-up

            Return ONLY JSON:
            {
              "relevance": 7.5,
              "clarity": 6.0,
              "technical": 5.0,
              "communication": 7.0,
              "aiFeedback": "2-3 specific constructive sentences.",
              "followUpQuestion": "One realistic interviewer follow-up question."
            }
            """.formatted(questionText, questionType, expectedPoints, transcript);

        return aiClient.chatJson(systemPrompt, userPrompt);
    }

    private static double safeDouble(JsonNode node, String field, double fallback) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return fallback;
        }
        return n.asDouble(fallback);
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...[truncated]";
    }

    private static double avg(Double a, Double b) {
        if (a == null && b == null) {
            return 0.0;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return (a + b) / 2.0;
    }

    private static double orZero(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
