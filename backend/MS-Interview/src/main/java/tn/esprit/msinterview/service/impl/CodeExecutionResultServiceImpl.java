package tn.esprit.msinterview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.ai.Judge0Client;
import tn.esprit.msinterview.ai.NvidiaAiClient;
import tn.esprit.msinterview.entity.CodeExecutionResult;
import tn.esprit.msinterview.repository.CodeExecutionResultRepository;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;
import tn.esprit.msinterview.service.CodeExecutionResultService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CodeExecutionResultServiceImpl implements CodeExecutionResultService {

    private final CodeExecutionResultRepository repository;
    private final SessionAnswerRepository answerRepository;
    private final InterviewQuestionRepository questionRepository;
    private final Judge0Client judge0Client;
    private final NvidiaAiClient aiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record LocalExecutionResult(String stdout, String stderr, String statusDescription) {}

    private record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {}

    @Override
    public List<TestCaseResult> runAgainstTestCases(Long questionId, String sourceCode, String language) {
        var question = questionRepository.findById(questionId).orElse(null);
        if (question == null || question.getMetadata() == null || question.getMetadata().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(question.getMetadata());
            JsonNode testCasesNode = resolveTestCasesNode(root);
            if (!testCasesNode.isArray()) {
                return List.of();
            }

            int timeLimitSeconds = root.path("timeLimitSeconds").asInt(2);
            String executableSource = sourceCode;
            List<TestCaseResult> results = new ArrayList<>();

            int index = 0;
            for (JsonNode testCase : testCasesNode) {
                String input = testCase.path("input").asText("");
                String expectedOutput = testCase.path("expectedOutput").asText("");
                boolean hidden = testCase.path("isHidden").asBoolean(false);

                Judge0Client.Judge0Result run = judge0Client.execute(
                    executableSource,
                        language,
                        input,
                        timeLimitSeconds
                );

                String stdout = safeTrim(run.stdout());
                String stderr = safeTrim(run.stderr());
                String statusDescription = safeTrim(run.statusDescription());

                if (shouldUseLocalJavaFallback(language, statusDescription, stderr, stdout)) {
                    LocalExecutionResult local = runJavaLocally(executableSource, input, timeLimitSeconds);
                    if (local != null) {
                        log.warn("Using local Java fallback because Judge0 failed with status='{}'", statusDescription);
                        stdout = safeTrim(local.stdout());
                        stderr = safeTrim(local.stderr());
                        statusDescription = safeTrim(local.statusDescription());
                    }
                }

                String actualOutput = stdout;

                String actualTrimmed = actualOutput
                    .trim()
                    .replaceAll("\\r\\n", "\n")
                    .replaceAll("\\r", "\n");

                String expectedTrimmed = expectedOutput
                    .trim()
                    .replaceAll("\\r\\n", "\n")
                    .replaceAll("\\r", "\n");

                boolean passed = actualTrimmed.equals(expectedTrimmed);

                results.add(new TestCaseResult(
                        index,
                        hidden ? "[hidden]" : input,
                        hidden ? "[hidden]" : expectedOutput,
                        actualOutput,
                    stderr,
                    statusDescription,
                        passed,
                        hidden
                ));

                index++;
            }

            return results;
        } catch (Exception ex) {
            log.warn("Failed to run test cases for question {}: {}", questionId, ex.getMessage());
            return List.of();
        }
    }

    private JsonNode resolveTestCasesNode(JsonNode root) {
        JsonNode explicitTests = root.path("testCases");
        if (explicitTests.isArray() && explicitTests.size() > 0) {
            return explicitTests;
        }

        JsonNode tryTheseTests = root.path("tryThese");
        if (tryTheseTests.isArray() && tryTheseTests.size() > 0) {
            return tryTheseTests;
        }

        return explicitTests;
    }

    @Override
    public List<CodeExecutionResult> getResultsByAnswer(Long answerId) {
        if (!answerRepository.existsById(answerId)) {
            return List.of();
        }
        return repository.findByAnswerId(answerId);
    }

    @Override
    public Optional<CodeExecutionResult> getLatestResult(Long answerId) {
        if (!answerRepository.existsById(answerId)) {
            return Optional.empty();
        }
        return repository.findLatest(answerId);
    }

    @Override
    public String generateComplexityNote(Long answerId, String sourceCode) {
        String fallback = "{\"timeComplexity\":\"Unknown\",\"spaceComplexity\":\"Unknown\",\"explanation\":\"Could not analyze\",\"qualityScore\":0,\"codeSmells\":[],\"suggestion\":\"N/A\"}";
        try {
            String systemPrompt = "You are an expert software engineer. Analyze code. Return only JSON.";
            String userPrompt = """
                Analyze this code and return ONLY this JSON:
                {
                  "timeComplexity": "O(n)",
                  "spaceComplexity": "O(1)",
                  "explanation": "brief explanation of why",
                  "qualityScore": 7.5,
                  "codeSmells": ["any issues"],
                  "suggestion": "one specific improvement"
                }

                CODE:
                %s
                """.formatted(sourceCode);

            JsonNode result = aiClient.chatJson(systemPrompt, userPrompt);
            return result.toString();
        } catch (Exception ex) {
            log.warn("Complexity generation failed for answer {}: {}", answerId, ex.getMessage());
            return fallback;
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean shouldUseLocalJavaFallback(String language,
                                               String statusDescription,
                                               String stderr,
                                               String stdout) {
        if (!"java".equalsIgnoreCase(safeTrim(language))) {
            return false;
        }

        String status = safeTrim(statusDescription).toLowerCase();
        if ("accepted".equals(status)) {
            return false;
        }

        String combined = (safeTrim(stderr) + "\n" + safeTrim(stdout) + "\n" + status).toLowerCase();
        return combined.contains("insufficient memory for the java runtime environment")
                || combined.contains("could not allocate metaspace")
                || combined.contains("failed to reserve memory")
                || combined.contains("no such file or directory @ rb_sysopen")
                || combined.contains("can't open file to dump replay data")
                || combined.contains("internal error");
    }

    private LocalExecutionResult runJavaLocally(String executableSource, String stdin, int timeLimitSeconds) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("msinterview-java-");
            Path mainJava = tempDir.resolve("Main.java");
            Files.writeString(mainJava, executableSource, StandardCharsets.UTF_8);

            ProcessResult compile = runProcess(
                    tempDir,
                    List.of(resolveJavaBinary("javac"), "Main.java"),
                    "",
                    Math.max(5, timeLimitSeconds + 5)
            );

            if (compile.timedOut()) {
                return new LocalExecutionResult("", "Local javac timed out", "Compilation Error");
            }

            if (compile.exitCode() != 0) {
                String compileError = safeTrim(compile.stderr());
                if (compileError.isBlank()) {
                    compileError = safeTrim(compile.stdout());
                }
                return new LocalExecutionResult("", compileError, "Compilation Error");
            }

            ProcessResult run = runProcess(
                    tempDir,
                    List.of(resolveJavaBinary("java"), "Main"),
                    stdin,
                    Math.max(5, timeLimitSeconds + 5)
            );

            if (run.timedOut()) {
                return new LocalExecutionResult("", "Local java execution timed out", "Time Limit Exceeded");
            }

            String runStdout = safeTrim(run.stdout());
            String runStderr = safeTrim(run.stderr());
            String status = run.exitCode() == 0 ? "Accepted" : "Runtime Error";
            if (!runStderr.isBlank()) {
                status = "Runtime Error";
            }

            return new LocalExecutionResult(runStdout, runStderr, status);
        } catch (Exception ex) {
            log.warn("Local Java fallback failed: {}", ex.getMessage());
            return null;
        } finally {
            cleanupDirectory(tempDir);
        }
    }

    private ProcessResult runProcess(Path workingDir,
                                     List<String> command,
                                     String stdin,
                                     int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir.toFile());
        processBuilder.redirectErrorStream(false);

        Process process = processBuilder.start();
        if (stdin != null && !stdin.isEmpty()) {
            process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        process.getOutputStream().close();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = finished ? process.exitValue() : -1;
        return new ProcessResult(exitCode, stdout, stderr, !finished);
    }

    private String resolveJavaBinary(String binaryName) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return binaryName;
        }

        Path javaPath = Path.of(javaHome, "bin", binaryName + (isWindows() ? ".exe" : ""));
        if (Files.exists(javaPath)) {
            return javaPath.toString();
        }

        Path jdkJavaPath = Path.of(javaHome).getParent();
        if (jdkJavaPath != null) {
            Path jdkBinary = jdkJavaPath.resolve("bin").resolve(binaryName + (isWindows() ? ".exe" : ""));
            if (Files.exists(jdkBinary)) {
                return jdkBinary.toString();
            }
        }

        return binaryName;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void cleanupDirectory(Path directory) {
        if (directory == null) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup only.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }
}
