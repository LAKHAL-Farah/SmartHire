package tn.esprit.msroadmap.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msroadmap.DTO.request.AnswerSubmissionDto;
import tn.esprit.msroadmap.DTO.response.AnswerResultDto;
import tn.esprit.msroadmap.DTO.response.InterviewResultDto;
import tn.esprit.msroadmap.DTO.response.InterviewSessionDto;
import tn.esprit.msroadmap.DTO.response.QuestionDto;
import tn.esprit.msroadmap.Entities.InterviewAnswer;
import tn.esprit.msroadmap.Entities.InterviewSession;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Exception.ServiceUnavailableException;
import tn.esprit.msroadmap.Repositories.InterviewAnswerRepository;
import tn.esprit.msroadmap.Repositories.InterviewSessionRepository;
import tn.esprit.msroadmap.ai.AiClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public InterviewSessionDto createSession(Long userId, String careerPath, String difficulty) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId must be a positive number");
        }
        if (careerPath == null || careerPath.isBlank()) {
            throw new BusinessException("careerPath is required");
        }
        if (difficulty == null || difficulty.isBlank()) {
            throw new BusinessException("difficulty is required");
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setCareerPath(careerPath.trim());
        session.setDifficulty(difficulty.trim().toUpperCase(Locale.ROOT));
        session.setStatus("IN_PROGRESS");
        session.setStartedAt(LocalDateTime.now());
        session.setQuestions(generateQuestionsWithAI(careerPath, difficulty));

        session = sessionRepository.save(session);
        return toDto(session);
    }

    @Transactional(readOnly = true)
    public QuestionDto getNextQuestion(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));

        List<String> questions = parseQuestions(session.getQuestions());
        int answeredCount = answerRepository.countBySessionId(sessionId);

        if (answeredCount >= questions.size()) {
            throw new BusinessException("Interview completed. Get score first.");
        }

        String questionText = questions.get(answeredCount);
        return QuestionDto.builder()
                .id((long) (answeredCount + 1))
                .question(questionText)
                .text(questionText)
                .order(answeredCount + 1)
                .total(questions.size())
                .build();
    }

    public AnswerResultDto submitAnswer(Long sessionId, AnswerSubmissionDto answer) {
        if (answer == null || answer.getAnswer() == null || answer.getAnswer().isBlank()) {
            throw new BusinessException("answer is required");
        }

        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));

        if (!"IN_PROGRESS".equalsIgnoreCase(session.getStatus())) {
            throw new BusinessException("Interview session is not active");
        }

        String currentQuestion = getCurrentQuestion(sessionId);

        String evaluation = evaluateAnswerWithAI(currentQuestion, answer.getAnswer());

        InterviewAnswer saved = new InterviewAnswer();
        saved.setSession(session);
        saved.setQuestionText(currentQuestion);
        saved.setUserAnswer(answer.getAnswer());
        saved.setAiEvaluation(evaluation);
        saved.setScore(extractScore(evaluation));
        saved.setSubmittedAt(LocalDateTime.now());
        answerRepository.save(saved);

        int answeredCount = answerRepository.countBySessionId(sessionId);
        List<String> questions = parseQuestions(session.getQuestions());

        boolean completed = answeredCount >= questions.size();
        if (completed) {
            session.setStatus("COMPLETED");
            session.setCompletedAt(LocalDateTime.now());
            session.setFinalScore(calculateFinalScore(sessionId));
            sessionRepository.save(session);
        }

        return AnswerResultDto.builder()
                .evaluation(evaluation)
                .score(saved.getScore())
                .completed(completed)
            .questionText(saved.getQuestionText())
            .userAnswer(saved.getUserAnswer())
                .build();
    }

    @Transactional(readOnly = true)
    public InterviewResultDto getSessionScore(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));

        List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderBySubmittedAtAsc(sessionId);
        List<String> questions = parseQuestions(session.getQuestions());

        if (answers.isEmpty()) {
            throw new BusinessException("No answers submitted for this interview session yet");
        }

        double finalScore = session.getFinalScore() != null
                ? session.getFinalScore()
                : calculateFinalScore(sessionId);

        List<AnswerResultDto> answerResults = answers.stream()
                .map(a -> AnswerResultDto.builder()
                        .evaluation(a.getAiEvaluation())
                        .score(a.getScore())
                        .completed(false)
                .questionText(a.getQuestionText())
                .userAnswer(a.getUserAnswer())
                        .build())
                .collect(Collectors.toList());

        return InterviewResultDto.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .finalScore(finalScore)
                .totalQuestions(questions.size())
                .answeredQuestions(answers.size())
                .answers(answerResults)
                .build();
    }

    @Transactional(readOnly = true)
    public List<InterviewSessionDto> getUserSessions(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId must be a positive number");
        }

        return sessionRepository.findByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private String generateQuestionsWithAI(String careerPath, String difficulty) {
        String prompt = String.format("""
            Generate 5 technical interview questions for a %s position at %s level.
            Return as JSON array of strings.
            """, careerPath, difficulty);

        try {
            String aiResponse = aiClient.call("You are an interviewer.", prompt);
            List<String> questions = parseQuestions(aiResponse);
            if (questions.isEmpty()) {
                throw new BusinessException("No questions generated by AI");
            }
            return objectMapper.writeValueAsString(questions);
        } catch (Exception ex) {
            log.warn("AI question generation failed: {}", ex.getMessage());
            throw new ServiceUnavailableException("Interview question generation service is currently unavailable");
        }
    }

    private String evaluateAnswerWithAI(String question, String answer) {
        String prompt = String.format("""
            Question: %s
            Candidate Answer: %s

            Evaluate this answer. Return JSON:
            {"score": 1-10, "feedback": "detailed feedback", "correctness": "correct|partial|incorrect"}
            """, question, answer);

        try {
            String aiResponse = aiClient.call("You are an interviewer evaluating answers.", prompt);
            String cleaned = cleanJson(aiResponse);
            JsonNode node = objectMapper.readTree(cleaned);
            int score = node.path("score").asInt(0);
            String feedback = node.path("feedback").asText("No feedback generated");
            String correctness = node.path("correctness").asText("partial");
            return String.format("Score: %d/10 | Correctness: %s | Feedback: %s", score, correctness, feedback);
        } catch (Exception ex) {
            log.warn("AI answer evaluation failed: {}", ex.getMessage());
            throw new ServiceUnavailableException("Interview answer evaluation service is currently unavailable");
        }
    }

    private List<String> parseQuestions(String rawQuestions) {
        if (rawQuestions == null || rawQuestions.isBlank()) {
            return new ArrayList<>();
        }

        try {
            String cleaned = cleanJson(rawQuestions);
            JsonNode root = objectMapper.readTree(cleaned);
            List<String> parsed = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode n : root) {
                    if (n.isTextual()) {
                        parsed.add(n.asText());
                    } else if (n.has("question")) {
                        parsed.add(n.get("question").asText());
                    } else if (n.has("text")) {
                        parsed.add(n.get("text").asText());
                    }
                }
            }

            return parsed;
        } catch (Exception ex) {
            log.warn("Could not parse question JSON directly: {}", ex.getMessage());
            try {
                String cleaned = rawQuestions.replace("```json", "").replace("```", "").trim();
                List<String> list = objectMapper.readValue(cleaned, new TypeReference<List<String>>() {
                });
                return list == null ? new ArrayList<>() : list;
            } catch (Exception ignored) {
                return new ArrayList<>();
            }
        }
    }

    private String getCurrentQuestion(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found: " + sessionId));

        List<String> questions = parseQuestions(session.getQuestions());
        int answeredCount = answerRepository.countBySessionId(sessionId);

        if (answeredCount >= questions.size()) {
            throw new BusinessException("No pending question. Interview already complete.");
        }
        return questions.get(answeredCount);
    }

    private Double extractScore(String evaluation) {
        if (evaluation == null || evaluation.isBlank()) {
            return 0.0;
        }

        Pattern pattern = Pattern.compile("(?i)score:\\s*(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(evaluation);
        if (matcher.find()) {
            try {
                double val = Double.parseDouble(matcher.group(1));
                return Math.max(0.0, Math.min(10.0, val));
            } catch (Exception ignored) {
                return 0.0;
            }
        }

        return 0.0;
    }

    private Double calculateFinalScore(Long sessionId) {
        List<InterviewAnswer> answers = answerRepository.findBySessionIdOrderBySubmittedAtAsc(sessionId);
        if (answers.isEmpty()) {
            return 0.0;
        }
        return answers.stream()
                .map(InterviewAnswer::getScore)
                .filter(s -> s != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private InterviewSessionDto toDto(InterviewSession session) {
        List<String> questions = parseQuestions(session.getQuestions());
        int answered = answerRepository.countBySessionId(session.getId());

        return InterviewSessionDto.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .careerPath(session.getCareerPath())
                .difficulty(session.getDifficulty())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .finalScore(session.getFinalScore())
                .totalQuestions(questions.size())
                .answeredQuestions(answered)
                .build();
    }

    private String cleanJson(String value) {
        return value.replace("```json", "").replace("```", "").trim();
    }
}
