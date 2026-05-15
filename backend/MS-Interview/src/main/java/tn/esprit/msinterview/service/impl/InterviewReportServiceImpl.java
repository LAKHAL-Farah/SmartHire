package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.NvidiaAiClient;
import tn.esprit.msinterview.dto.QuestionStressScoreDTO;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.InterviewReport;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.repository.AnswerEvaluationRepository;
import tn.esprit.msinterview.repository.InterviewReportRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.service.InterviewReportService;
import tn.esprit.msinterview.service.StressAggregatorService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewReportServiceImpl implements InterviewReportService {

    private final InterviewReportRepository interviewReportRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final AnswerEvaluationRepository answerEvaluationRepository;
    private final NvidiaAiClient aiClient;
    private final StressAggregatorService stressAggregator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int SAMPLE_FEEDBACK_MAX_CHARS = 1400;

    @Override
    @Transactional
    public InterviewReport generateReport(Long sessionId) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        InterviewReport existing = interviewReportRepository.findBySessionId(sessionId).orElse(null);
        if (existing != null) {
            stressAggregator.clearSession(sessionId);
            return existing;
        }

        List<AnswerEvaluation> evaluations = answerEvaluationRepository.findByAnswerSessionId(sessionId);

        double finalScore = round2(calculateFinalScore(evaluations));
        double contentAvg = round2(calculateAverageScore(evaluations, "content"));
        double voiceAvg = round2(calculateAverageScore(evaluations, "voice"));
        double technicalAvg = round2(calculateAverageScore(evaluations, "technical"));
        double presenceAvg = round2(calculateAverageScore(evaluations, "presence"));
        StressAggregatorService.StressSessionSummary stressSummary = stressAggregator.getSessionSummary(sessionId);
        double avgStressScore = round2(stressSummary.avgScore());
        List<StressAggregatorService.StressQuestionSummary> finalizedQuestionSummaries =
            stressAggregator.getFinalizedQuestionSummaries(sessionId);

        List<QuestionStressScoreDTO> questionStressScores =
            buildQuestionStressScores(session, evaluations, finalizedQuestionSummaries);
        String questionStressScoresJson = writeQuestionStressScores(questionStressScores);

        session.setOverallStressScore(avgStressScore);
        interviewSessionRepository.save(session);

        NarrativeData narrative = generateNarrative(sessionId, finalScore, contentAvg, technicalAvg, voiceAvg, presenceAvg, evaluations);
        int percentileRank = computePercentileRank(sessionId, finalScore);

        InterviewReport report = InterviewReport.builder()
                .session(session)
                .userId(session.getUserId())
                .finalScore(finalScore)
                .percentileRank((double) percentileRank)
                .contentAvg(contentAvg)
                .voiceAvg(voiceAvg)
                .technicalAvg(technicalAvg)
                .presenceAvg(presenceAvg)
                .overallStressLevel(stressSummary.level())
                .avgStressScore(avgStressScore)
                .questionStressScores(questionStressScoresJson)
                .strengths(narrative.strengths())
                .weaknesses(narrative.weaknesses())
                .recommendations(narrative.recommendations())
                .recruiterVerdict(narrative.recruiterVerdict())
                .pdfUrl(generatePdfReport(sessionId))
                .generatedAt(LocalDateTime.now())
                .build();

            InterviewReport saved = interviewReportRepository.save(report);
            stressAggregator.clearSession(sessionId);
            return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewReport getReportBySession(Long sessionId) {
        return interviewReportRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found for session: " + sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewReport> getReportsByUser(Long userId) {
        return interviewReportRepository.findByUserIdOrderByGeneratedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewReport getReportById(Long id) {
        return interviewReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public int computePercentileRank(Long sessionId, Double finalScore) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        long totalReports = interviewReportRepository.countBySessionCareerPathId(session.getCareerPathId());
        long reportsBelowScore = interviewReportRepository.countBelowScore(session.getCareerPathId(), finalScore);

        if (totalReports == 0) {
            return 50;
        }

        return (int) ((reportsBelowScore * 100) / totalReports);
    }

    @Override
    @Transactional
    public String generatePdf(Long reportId) {
        InterviewReport report = interviewReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        String pdfUrl = "/reports/report_" + reportId + ".pdf";
        report.setPdfUrl(pdfUrl);
        interviewReportRepository.save(report);
        return pdfUrl;
    }

    private double calculateFinalScore(List<AnswerEvaluation> evaluations) {
        if (evaluations.isEmpty()) {
            return 0.0;
        }

        return evaluations.stream()
                .mapToDouble(e -> e.getOverallScore() != null ? e.getOverallScore() : 0.0)
                .average()
                .orElse(0.0);
    }

    private double calculateAverageScore(List<AnswerEvaluation> evaluations, String type) {
        if (evaluations.isEmpty()) {
            return 0.0;
        }

        return evaluations.stream()
                .mapToDouble(e -> switch (type) {
                    case "content" -> average(e.getContentScore(), e.getClarityScore());
                    case "voice" -> average(e.getConfidenceScore(), e.getToneScore());
                    case "technical" -> e.getTechnicalScore() != null ? e.getTechnicalScore() : 0.0;
                    case "presence" -> average(e.getPostureScore(), e.getEyeContactScore());
                    default -> 0.0;
                })
                .average()
                .orElse(0.0);
    }

    private NarrativeData generateNarrative(Long sessionId,
                                            double finalScore,
                                            double contentAvg,
                                            double technicalAvg,
                                            double voiceAvg,
                                            double presenceAvg,
                                            List<AnswerEvaluation> evaluations) {
        String fallbackStrengths = "Good effort with consistent participation across interview questions.";
        String fallbackWeaknesses = "Some responses lacked depth and stronger justification.";
        String fallbackRecommendations = "Practice concise structured answers and include concrete examples.";
        String fallbackVerdict = generateRecruiterVerdict(finalScore);

        try {
            String sampleFeedback = evaluations.stream()
                    .map(AnswerEvaluation::getAiFeedback)
                    .filter(v -> v != null && !v.isBlank())
                    .limit(5)
                    .collect(Collectors.joining("\n- ", "- ", ""));
            sampleFeedback = truncate(sampleFeedback, SAMPLE_FEEDBACK_MAX_CHARS);

            String systemPrompt = """
                You are a senior technical recruiter.
                Return ONLY valid JSON.
                """;

            String userPrompt = """
                Session ID: %d

                Scores (0-10):
                - finalScore: %.2f
                - contentAvg: %.2f
                - technicalAvg: %.2f
                - voiceAvg: %.2f
                - presenceAvg: %.2f

                Sample evaluator feedback:
                %s

                Verdict consistency rule:
                - finalScore >= 8.0 => STRONG HIRE
                - finalScore >= 6.5 and < 8.0 => HIRE
                - finalScore >= 5.0 and < 6.5 => MAYBE
                - finalScore < 5.0 => NO HIRE

                Return ONLY this JSON:
                {
                  "strengths": "2-3 concrete strengths, concise",
                  "weaknesses": "2-3 concrete weaknesses, concise",
                  "recommendations": "3 actionable recommendations, concise",
                  "recruiterVerdict": "<VERDICT>: one-line realistic rationale"
                }
                """.formatted(sessionId, finalScore, contentAvg, technicalAvg, voiceAvg, presenceAvg, sampleFeedback);

            JsonNode result = aiClient.chatJson(systemPrompt, userPrompt);
            String strengths = nonBlank(result.path("strengths").asText(null), fallbackStrengths);
            String weaknesses = nonBlank(result.path("weaknesses").asText(null), fallbackWeaknesses);
            String recommendations = nonBlank(result.path("recommendations").asText(null), fallbackRecommendations);
            String recruiterVerdict = nonBlank(result.path("recruiterVerdict").asText(null), fallbackVerdict);

            return new NarrativeData(strengths, weaknesses, recommendations, recruiterVerdict);
        } catch (Exception ex) {
            log.warn("NVIDIA narrative generation failed for session {}: {}", sessionId, ex.getMessage());
            return new NarrativeData(fallbackStrengths, fallbackWeaknesses, fallbackRecommendations, fallbackVerdict);
        }
    }

    private String generateRecruiterVerdict(Double finalScore) {
        if (finalScore == null) {
            return "NO HIRE - Evaluation data is insufficient.";
        }

        if (finalScore >= 8.0) {
            return "STRONG HIRE - Excellent technical and communication performance.";
        }
        if (finalScore >= 6.5) {
            return "HIRE - Solid candidate with good core skills.";
        }
        if (finalScore >= 5.0) {
            return "MAYBE - Shows potential, needs stronger depth and consistency.";
        }
        return "NO HIRE - Current performance is below the expected bar.";
    }

    private String generatePdfReport(Long sessionId) {
        return "/reports/report_" + sessionId + ".pdf";
    }

    private static double average(Double a, Double b) {
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

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private List<QuestionStressScoreDTO> buildQuestionStressScores(
            InterviewSession session,
            List<AnswerEvaluation> evaluations,
            List<StressAggregatorService.StressQuestionSummary> finalizedQuestionSummaries
    ) {
        boolean hasEvaluationStress = evaluations != null && evaluations.stream()
                .anyMatch(evaluation -> evaluation != null && evaluation.getAvgStressScore() != null);
        boolean hasFinalizedStress = finalizedQuestionSummaries != null && !finalizedQuestionSummaries.isEmpty();

        if (!hasEvaluationStress && !hasFinalizedStress) {
            return List.of();
        }

        Map<Long, Integer> questionIndexByQuestionId = new HashMap<>();
        if (session.getQuestionOrders() != null) {
            session.getQuestionOrders().stream()
                    .sorted(Comparator.comparingInt(order -> order.getQuestionOrder() == null ? Integer.MAX_VALUE : order.getQuestionOrder()))
                    .forEach(order -> {
                        if (order.getQuestion() != null && order.getQuestion().getId() != null) {
                            int index = Math.max(0, (order.getQuestionOrder() == null ? 1 : order.getQuestionOrder()) - 1);
                            questionIndexByQuestionId.put(order.getQuestion().getId(), index);
                        }
                    });
        }

        Map<Integer, QuestionStressScoreDTO> byQuestionIndex = new LinkedHashMap<>();

        if (finalizedQuestionSummaries != null) {
            for (StressAggregatorService.StressQuestionSummary summary : finalizedQuestionSummaries) {
                if (summary == null) {
                    continue;
                }

                int fallbackIndex = byQuestionIndex.size();
                int questionIndex = questionIndexByQuestionId.getOrDefault(summary.questionId(), fallbackIndex);
                String level = (summary.level() == null || summary.level().isBlank())
                        ? levelFromStressScore(summary.avgScore())
                        : summary.level();

                byQuestionIndex.put(
                        questionIndex,
                        QuestionStressScoreDTO.builder()
                                .questionIndex(questionIndex)
                                .score(round2(summary.avgScore()))
                                .level(level)
                                .build()
                );
            }
        }

        for (AnswerEvaluation evaluation : evaluations) {
            if (evaluation == null || evaluation.getAvgStressScore() == null) {
                continue;
            }

            if (evaluation.getAnswer() == null || evaluation.getAnswer().getQuestion() == null) {
                continue;
            }

            if (evaluation.getAnswer().isFollowUp()) {
                continue;
            }

            Long questionId = evaluation.getAnswer().getQuestion().getId();
            if (questionId == null) {
                continue;
            }

            int fallbackIndex = byQuestionIndex.size();
            int questionIndex = questionIndexByQuestionId.getOrDefault(questionId, fallbackIndex);

            String level = evaluation.getStressPeakLevel();
            if (level == null || level.isBlank()) {
                level = levelFromStressScore(evaluation.getAvgStressScore());
            }

            byQuestionIndex.put(
                    questionIndex,
                    QuestionStressScoreDTO.builder()
                            .questionIndex(questionIndex)
                            .score(round2(evaluation.getAvgStressScore()))
                            .level(level)
                            .build()
            );
        }

        return byQuestionIndex.values().stream()
                .sorted(Comparator.comparingInt(score -> score.getQuestionIndex() == null ? Integer.MAX_VALUE : score.getQuestionIndex()))
                .collect(Collectors.toList());
    }

    private String writeQuestionStressScores(List<QuestionStressScoreDTO> scores) {
        try {
            return objectMapper.writeValueAsString(scores == null ? List.of() : scores);
        } catch (Exception ex) {
            log.warn("Failed to serialize question stress scores: {}", ex.getMessage());
            return "[]";
        }
    }

    private String levelFromStressScore(Double score) {
        if (score == null) {
            return "low";
        }
        if (score > 0.6) {
            return "high";
        }
        if (score > 0.35) {
            return "medium";
        }
        return "low";
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

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record NarrativeData(String strengths, String weaknesses, String recommendations, String recruiterVerdict) {
    }
}
