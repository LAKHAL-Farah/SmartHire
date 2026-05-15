package tn.esprit.msinterview.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import tn.esprit.msinterview.entity.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DTOMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    // InterviewQuestion Mapping
    public static InterviewQuestionDTO toQuestionDTO(InterviewQuestion entity) {
        if (entity == null) return null;
        return InterviewQuestionDTO.builder()
                .id(entity.getId())
                .careerPathId(entity.getCareerPathId())
                .roleType(entity.getRoleType())
                .questionText(entity.getQuestionText())
                .type(entity.getType())
                .difficulty(entity.getDifficulty())
                .domain(entity.getDomain())
                .category(entity.getCategory())
                .expectedPoints(entity.getExpectedPoints())
                .followUps(entity.getFollowUps())
                .hints(entity.getHints())
                .idealAnswer(entity.getIdealAnswer())
                .sampleCode(entity.getSampleCode())
                .tags(entity.getTags())
                .metadata(entity.getMetadata())
                .isActive(entity.isActive())
                .build();
    }

    // InterviewSession Mapping (with nested DTOs)
    public static InterviewSessionDTO toSessionDTO(InterviewSession entity) {
        if (entity == null) return null;
        return InterviewSessionDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .careerPathId(entity.getCareerPathId())
                .roleType(entity.getRoleType())
                .mode(entity.getMode())
                .type(entity.getType())
                .status(entity.getStatus())
                .totalScore(entity.getTotalScore())
                .currentQuestionIndex(entity.getCurrentQuestionIndex())
                .durationSeconds(entity.getDurationSeconds())
                .startedAt(entity.getStartedAt())
                .endedAt(entity.getEndedAt())
                .isPressureMode(entity.isPressureMode())
                .pressureEventsTriggered(entity.getPressureEventsTriggered())
                .questionOrders(entity.getQuestionOrders() != null ? 
                    entity.getQuestionOrders().stream()
                        .map(DTOMapper::toQuestionOrderDTO)
                        .collect(Collectors.toList()) : null)
                .answers(entity.getAnswers() != null ?
                    entity.getAnswers().stream()
                        .map(DTOMapper::toAnswerDTO)
                        .collect(Collectors.toList()) : null)
                .pressureEvents(entity.getPressureEvents() != null ?
                    entity.getPressureEvents().stream()
                        .map(DTOMapper::toPressureEventDTO)
                        .collect(Collectors.toList()) : null)
                .report(entity.getReport() != null ? toReportDTO(entity.getReport()) : null)
                .build();
    }

    // SessionQuestionOrder Mapping
    public static SessionQuestionOrderDTO toQuestionOrderDTO(SessionQuestionOrder entity) {
        if (entity == null) return null;
        return SessionQuestionOrderDTO.builder()
                .id(entity.getId())
                .sessionId(entity.getSession() != null ? entity.getSession().getId() : null)
                .questionId(entity.getQuestion() != null ? entity.getQuestion().getId() : null)
                .questionOrder(entity.getQuestionOrder())
                .nextQuestionId(entity.getNextQuestionId())
                .timeAllottedSeconds(entity.getTimeAllottedSeconds())
                .wasSkipped(entity.isWasSkipped())
                .question(entity.getQuestion() != null ? toQuestionDTO(entity.getQuestion()) : null)
                .build();
    }

    // SessionAnswer Mapping (with nested evaluations)
    public static SessionAnswerDTO toAnswerDTO(SessionAnswer entity) {
        if (entity == null) return null;
        return SessionAnswerDTO.builder()
                .id(entity.getId())
                .sessionId(entity.getSession() != null ? entity.getSession().getId() : null)
                .questionId(entity.getQuestion() != null ? entity.getQuestion().getId() : null)
                .answerText(entity.getAnswerText())
                .codeAnswer(entity.getCodeAnswer())
                .codeOutput(entity.getCodeOutput())
                .codeLanguage(entity.getCodeLanguage())
                .videoUrl(entity.getVideoUrl())
                .audioUrl(entity.getAudioUrl())
                .retryCount(entity.getRetryCount())
                .timeSpentSeconds(entity.getTimeSpentSeconds())
                .submittedAt(entity.getSubmittedAt())
                .isFollowUp(entity.isFollowUp())
                .parentAnswerId(entity.getParentAnswer() != null ? entity.getParentAnswer().getId() : null)
                .answerEvaluation(entity.getAnswerEvaluation() != null ? toEvaluationDTO(entity.getAnswerEvaluation()) : null)
                .codeExecutionResult(entity.getCodeExecutionResult() != null ? toCodeExecutionDTO(entity.getCodeExecutionResult()) : null)
                .architectureDiagram(entity.getArchitectureDiagram() != null ? toArchitectureDTO(entity.getArchitectureDiagram()) : null)
                .mlScenarioAnswer(entity.getMlScenarioAnswer() != null ? toMLScenarioDTO(entity.getMlScenarioAnswer()) : null)
                .build();
    }

    // AnswerEvaluation Mapping
    public static AnswerEvaluationDTO toEvaluationDTO(AnswerEvaluation entity) {
        if (entity == null) return null;
        return AnswerEvaluationDTO.builder()
                .id(entity.getId())
                .answerId(entity.getAnswer() != null ? entity.getAnswer().getId() : null)
                .contentScore(entity.getContentScore())
                .clarityScore(entity.getClarityScore())
                .technicalScore(entity.getTechnicalScore())
                .codeCorrectnessScore(entity.getCodeCorrectnessScore())
                .codeComplexityNote(entity.getCodeComplexityNote())
                .confidenceScore(entity.getConfidenceScore())
                .toneScore(entity.getToneScore())
                .emotionScore(entity.getEmotionScore())
                .speechRate(entity.getSpeechRate())
                .hesitationCount(entity.getHesitationCount())
                .postureScore(entity.getPostureScore())
                .eyeContactScore(entity.getEyeContactScore())
                .expressionScore(entity.getExpressionScore())
                .overallScore(entity.getOverallScore())
                .aiFeedback(entity.getAiFeedback())
                .followUpGenerated(entity.getFollowUpGenerated())
                .avgStressScore(entity.getAvgStressScore())
                .stressPeakLevel(entity.getStressPeakLevel())
                .stressReadingCount(entity.getStressReadingCount())
                .stressTimeline(entity.getStressTimeline())
                .build();
    }

    // CodeExecutionResult Mapping
    public static CodeExecutionResultDTO toCodeExecutionDTO(CodeExecutionResult entity) {
        if (entity == null) return null;
        return CodeExecutionResultDTO.builder()
                .id(entity.getId())
                .answerId(entity.getAnswer() != null ? entity.getAnswer().getId() : null)
                .language(entity.getLanguage())
                .sourceCode(entity.getSourceCode())
                .stdout(entity.getStdout())
                .stderr(entity.getStderr())
                .statusDescription(entity.getStatusDescription())
                .testCasesPassed(entity.getTestCasesPassed())
                .testCasesTotal(entity.getTestCasesTotal())
                .executionTimeMs(entity.getExecutionTimeMs())
                .memoryUsedKb(entity.getMemoryUsedKb())
                .complexityNote(entity.getComplexityNote())
                .build();
    }

    // ArchitectureDiagram Mapping
    public static ArchitectureDiagramDTO toArchitectureDTO(ArchitectureDiagram entity) {
        if (entity == null) return null;
        return ArchitectureDiagramDTO.builder()
                .id(entity.getId())
                .answerId(entity.getAnswer() != null ? entity.getAnswer().getId() : null)
                .diagramJson(entity.getDiagramJson())
                .nodeCount(entity.getNodeCount())
                .edgeCount(entity.getEdgeCount())
                .componentTypes(entity.getComponentTypes())
                .designScore(entity.getDesignScore())
                .aiDesignScore(entity.getDesignScore())
                .requirementsMet(entity.getRequirementsMet())
                .requirementsMissed(entity.getRequirementsMissed())
                .aiFeedback(entity.getAiFeedback())
                .followUpGenerated(entity.getFollowUpGenerated())
                .evaluatedAt(entity.getEvaluatedAt())
                .build();
    }

    // MLScenarioAnswer Mapping
    public static MLScenarioAnswerDTO toMLScenarioDTO(MLScenarioAnswer entity) {
        if (entity == null) return null;
        return MLScenarioAnswerDTO.builder()
                .id(entity.getId())
                .answerId(entity.getAnswer() != null ? entity.getAnswer().getId() : null)
                .modelChosen(entity.getModelChosen())
                .featuresDescribed(entity.getFeaturesDescribed())
                .metricsDescribed(entity.getMetricsDescribed())
                .deploymentStrategy(entity.getDeploymentStrategy())
                .dataPreprocessing(entity.getDataPreprocessing())
                .evaluationStrategy(entity.getEvaluationStrategy())
                .extractedConcepts(entity.getExtractedConcepts())
                .aiEvaluationScore(entity.getAiEvaluationScore())
                .build();
    }

    // PressureEvent Mapping
    public static PressureEventDTO toPressureEventDTO(PressureEvent entity) {
        if (entity == null) return null;
        return PressureEventDTO.builder()
                .id(entity.getId())
                .sessionId(entity.getSession() != null ? entity.getSession().getId() : null)
                .triggeredAt(entity.getTriggeredAt())
                .eventType(entity.getEventType())
                .questionIdAtTrigger(entity.getQuestionIdAtTrigger())
                .candidateReacted(entity.isCandidateReacted())
                .reactionTimeMs(entity.getReactionTimeMs())
                .build();
    }

    // InterviewReport Mapping
    public static InterviewReportDTO toReportDTO(InterviewReport entity) {
        if (entity == null) return null;
        return InterviewReportDTO.builder()
                .id(entity.getId())
                .sessionId(entity.getSession() != null ? entity.getSession().getId() : null)
                .userId(entity.getUserId())
                .finalScore(entity.getFinalScore())
                .percentileRank(entity.getPercentileRank())
                .contentAvg(entity.getContentAvg())
                .voiceAvg(entity.getVoiceAvg())
                .technicalAvg(entity.getTechnicalAvg())
                .presenceAvg(entity.getPresenceAvg())
                .overallStressLevel(entity.getOverallStressLevel())
                .avgStressScore(entity.getAvgStressScore())
                .questionStressScores(parseQuestionStressScores(entity.getQuestionStressScores()))
                .strengths(entity.getStrengths())
                .weaknesses(entity.getWeaknesses())
                .recommendations(entity.getRecommendations())
                .recruiterVerdict(entity.getRecruiterVerdict())
                .pdfUrl(entity.getPdfUrl())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }

    private static List<QuestionStressScoreDTO> parseQuestionStressScores(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return JSON.readValue(rawJson, new TypeReference<List<QuestionStressScoreDTO>>() {});
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    // InterviewStreak Mapping
    public static InterviewStreakDTO toStreakDTO(InterviewStreak entity) {
        if (entity == null) return null;
        return InterviewStreakDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .currentStreak(entity.getCurrentStreak())
                .longestStreak(entity.getLongestStreak())
                .lastSessionDate(entity.getLastSessionDate())
                .totalSessionsCompleted(entity.getTotalSessionsCompleted())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // QuestionBookmark Mapping
    public static QuestionBookmarkDTO toBookmarkDTO(QuestionBookmark entity) {
        if (entity == null) return null;
        return QuestionBookmarkDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .questionId(entity.getQuestion() != null ? entity.getQuestion().getId() : null)
                .note(entity.getNote())
                .tagLabel(entity.getTagLabel())
                .savedAt(entity.getSavedAt())
                .question(entity.getQuestion() != null ? toQuestionDTO(entity.getQuestion()) : null)
                .build();
    }

    // List mappers
    public static List<InterviewQuestionDTO> toQuestionDTOList(List<InterviewQuestion> entities) {
        return entities != null ? 
            entities.stream().map(DTOMapper::toQuestionDTO).collect(Collectors.toList()) : null;
    }

    public static List<SessionAnswerDTO> toAnswerDTOList(List<SessionAnswer> entities) {
        return entities != null ?
            entities.stream().map(DTOMapper::toAnswerDTO).collect(Collectors.toList()) : null;
    }

    public static List<PressureEventDTO> toPressureEventDTOList(List<PressureEvent> entities) {
        return entities != null ?
            entities.stream().map(DTOMapper::toPressureEventDTO).collect(Collectors.toList()) : null;
    }

    // Alias for SessionQuestionOrder
    public static SessionQuestionOrderDTO toSessionQuestionOrderDTO(SessionQuestionOrder entity) {
        return toQuestionOrderDTO(entity);
    }
}
