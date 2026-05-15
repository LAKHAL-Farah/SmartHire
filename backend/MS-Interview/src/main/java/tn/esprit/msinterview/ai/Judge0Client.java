package tn.esprit.msinterview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j
public class Judge0Client {

    @Value("${judge0.base-url:http://localhost:2358}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern BASE64_SANITIZER = Pattern.compile("[^A-Za-z0-9+/=]");

    private static final Map<String, Integer> LANGUAGE_IDS = Map.of(
        "java",       62,
        "python",     71,
        "javascript", 63,
        "cpp",        54
    );

    public record Judge0Result(
        String stdout,
        String stderr,
        String statusDescription,
        Long timeMs,
        Long memoryKb,
        boolean accepted
    ) {}

    public Judge0Result execute(String sourceCode, String language,
                                String stdin, int timeLimitSeconds) {
        log.info("Judge0Client.execute() language={}", language);

        String normalizedLanguage = language == null ? "" : language.trim().toLowerCase();
        Integer languageId = LANGUAGE_IDS.getOrDefault(normalizedLanguage, 71);
        String safeSourceCode = sourceCode == null ? "" : sourceCode;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("source_code", Base64.getEncoder()
                .encodeToString(safeSourceCode.getBytes(StandardCharsets.UTF_8)));
            body.put("language_id", languageId);
            body.put("stdin", stdin != null && !stdin.isBlank()
                ? Base64.getEncoder().encodeToString(stdin.getBytes(StandardCharsets.UTF_8))
                : "");
            body.put("cpu_time_limit", timeLimitSeconds);
            body.put("memory_limit", 4096000);
            body.put("enable_per_process_and_thread_time_limit", true);
            body.put("enable_per_process_and_thread_memory_limit", true);
            body.put("base64_encoded", true);

            String payload = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/submissions?wait=true&base64_encoded=true",
                request, String.class
            );

            if (response.getBody() == null) {
                return new Judge0Result("", "No response from Judge0",
                    "Internal Error", 0L, 0L, false);
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            String stdout = decodeBase64(root.path("stdout").asText(""));
            String stderr = joinNonBlank(
                decodeBase64(root.path("stderr").asText("")),
                decodeBase64(root.path("compile_output").asText("")),
                decodeBase64(root.path("message").asText(""))
            );
            String status = root.path("status").path("description").asText("Unknown");
            long timeMs = (long) (root.path("time").asDouble(0) * 1000);
            long memKb = root.path("memory").asLong(0);
            boolean accepted = "Accepted".equals(status);

            log.info("Judge0 result: status={} time={}ms", status, timeMs);
            return new Judge0Result(stdout, stderr, status, timeMs, memKb, accepted);

        } catch (Exception e) {
            log.error("Judge0Client error: {}", e.getMessage());
            return new Judge0Result("", "Execution failed: " + e.getMessage(),
                "Internal Error", 0L, 0L, false);
        }
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }

        String cleaned = BASE64_SANITIZER.matcher(encoded).replaceAll("");
        if (cleaned.isBlank()) {
            return encoded;
        }

        int remainder = cleaned.length() % 4;
        if (remainder != 0) {
            cleaned = cleaned + "=".repeat(4 - remainder);
        }

        try {
            return new String(Base64.getDecoder().decode(cleaned), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded;
        }
    }

    private String joinNonBlank(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }
}
