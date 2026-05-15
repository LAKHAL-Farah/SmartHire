package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.CodeExecutionResult;

import java.util.List;
import java.util.Optional;

public interface CodeExecutionResultService {
    record TestCaseResult(
            int index,
            String input,
            String expectedOutput,
            String actualOutput,
            String stderr,
            String statusDescription,
            boolean passed,
            boolean isHidden
    ) {}

    List<TestCaseResult> runAgainstTestCases(Long questionId, String sourceCode, String language);
    List<CodeExecutionResult> getResultsByAnswer(Long answerId);
    Optional<CodeExecutionResult> getLatestResult(Long answerId);
    String generateComplexityNote(Long answerId, String sourceCode);
}
