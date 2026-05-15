package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.dto.CodeExecutionResultDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.dto.SessionAnswerDTO;
import tn.esprit.msinterview.entity.CodeExecutionResult;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;
import tn.esprit.msinterview.exception.ResourceNotFoundException;
import tn.esprit.msinterview.repository.CodeExecutionResultRepository;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.AnswerEvaluationService;
import tn.esprit.msinterview.service.CodeExecutionResultService;
import tn.esprit.msinterview.service.SessionQuestionOrderService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/code")
@RequiredArgsConstructor
public class CodeExecutionResultController {

    private final CodeExecutionResultService codeExecutionService;
    private final CodeExecutionResultRepository codeExecutionResultRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final SessionQuestionOrderService questionOrderService;
    private final AnswerEvaluationService answerEvaluationService;

    public record ExecuteCodeRequest(Long answerId, Long questionId, String sourceCode, String language) {
    }

    public record SubmitCodeRequest(Long sessionId,
                    Long questionId,
                    String sourceCode,
                    String language,
                    String explanation) {
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeCode(@RequestBody ExecuteCodeRequest request) {
    List<CodeExecutionResultService.TestCaseResult> testCaseResults = codeExecutionService.runAgainstTestCases(
        request.questionId(),
        request.sourceCode(),
        request.language()
    );

    int totalCount = testCaseResults.size();
    int passedCount = (int) testCaseResults.stream().filter(CodeExecutionResultService.TestCaseResult::passed).count();

    String stderr = testCaseResults.stream()
        .map(CodeExecutionResultService.TestCaseResult::stderr)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse("");

    String statusDescription = testCaseResults.stream()
        .map(CodeExecutionResultService.TestCaseResult::statusDescription)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .filter(value -> !"Accepted".equalsIgnoreCase(value))
        .findFirst()
        .orElse(totalCount > 0 && passedCount == totalCount ? "Accepted" : "Wrong Answer");

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("testCaseResults", testCaseResults);
    response.put("passedCount", passedCount);
    response.put("totalCount", totalCount);
    response.put("allPassed", totalCount > 0 && passedCount == totalCount);
    response.put("stderr", stderr);
    response.put("statusDescription", statusDescription);
    response.put("answerId", request.answerId());

    return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<SessionAnswerDTO> submitCode(@RequestBody SubmitCodeRequest request) {
    InterviewSession session = sessionRepository.findById(request.sessionId())
        .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + request.sessionId()));

    InterviewQuestion question = questionRepository.findById(request.questionId())
        .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + request.questionId()));

    List<CodeExecutionResultService.TestCaseResult> testCaseResults = codeExecutionService.runAgainstTestCases(
        request.questionId(),
        request.sourceCode(),
        request.language()
    );

    int totalCount = testCaseResults.size();
    int passedCount = (int) testCaseResults.stream().filter(CodeExecutionResultService.TestCaseResult::passed).count();
    boolean allPassed = totalCount > 0 && passedCount == totalCount;

    String stderr = testCaseResults.stream()
        .map(CodeExecutionResultService.TestCaseResult::stderr)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse(allPassed ? "" : "One or more test cases failed");

    String statusDescription = testCaseResults.stream()
        .map(CodeExecutionResultService.TestCaseResult::statusDescription)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .filter(value -> !"Accepted".equalsIgnoreCase(value))
        .findFirst()
        .orElse(allPassed ? "Accepted" : "Wrong Answer");

    CodeLanguage codeLanguage = parseLanguage(request.language());

    SessionAnswer answer = SessionAnswer.builder()
        .session(session)
        .question(question)
        .answerText(request.explanation())
        .codeAnswer(request.sourceCode())
        .codeLanguage(codeLanguage)
        .retryCount(0)
        .isFollowUp(false)
        .submittedAt(LocalDateTime.now())
        .build();

    answer = sessionAnswerRepository.save(answer);

    String complexityNote = codeExecutionService.generateComplexityNote(answer.getId(), request.sourceCode());

    CodeExecutionResult result = CodeExecutionResult.builder()
        .answer(answer)
        .language(codeLanguage)
        .sourceCode(request.sourceCode())
        .stdout(testCaseResults.stream().map(CodeExecutionResultService.TestCaseResult::actualOutput).collect(Collectors.joining("\n")))
        .stderr(stderr)
        .statusDescription(statusDescription)
        .testCasesPassed(passedCount)
        .testCasesTotal(totalCount)
        .executionTimeMs(0L)
        .memoryUsedKb(0L)
        .complexityNote(complexityNote)
        .build();

    codeExecutionResultRepository.save(result);
    questionOrderService.advanceToNextQuestion(request.sessionId());
    answerEvaluationService.triggerEvaluation(answer.getId());

    return ResponseEntity.status(HttpStatus.ACCEPTED).body(DTOMapper.toAnswerDTO(answer));
    }

    @GetMapping("/results/{answerId}")
    public ResponseEntity<List<CodeExecutionResultDTO>> getResultsByAnswer(@PathVariable Long answerId) {
        List<CodeExecutionResult> results = codeExecutionService.getResultsByAnswer(answerId);
        return ResponseEntity.ok(results.stream().map(DTOMapper::toCodeExecutionDTO).toList());
    }

    @GetMapping("/results/{answerId}/latest")
    public ResponseEntity<CodeExecutionResultDTO> getLatestResult(@PathVariable Long answerId) {
        Optional<CodeExecutionResult> result = codeExecutionService.getLatestResult(answerId);
        return result.map(r -> ResponseEntity.ok(DTOMapper.toCodeExecutionDTO(r))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private CodeLanguage parseLanguage(String language) {
        if (language == null || language.isBlank()) {
            return CodeLanguage.PYTHON;
        }

        try {
            return CodeLanguage.valueOf(language.trim().toUpperCase());
        } catch (Exception ignored) {
            return CodeLanguage.PYTHON;
        }
    }
}
