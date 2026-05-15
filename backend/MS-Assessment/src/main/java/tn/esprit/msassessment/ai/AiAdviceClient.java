package tn.esprit.msassessment.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tn.esprit.msassessment.entity.AssessmentSession;
import tn.esprit.msassessment.entity.SessionAnswer;
import tn.esprit.msassessment.repository.SessionAnswerRepository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the Python AI-Advice microservice for personalised advice.
 * Falls back to {@link AssessmentAdviceService} (rule-based) if the Python service is unavailable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiAdviceClient {

    @Value("${smarthire.ai.advice.enabled:false}")
    private boolean enabled;

    @Value("${smarthire.ai.advice.base-url:http://localhost:8090}")
    private String baseUrl;

    private final AssessmentAdviceService fallbackAdviceService;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final ObjectMapper objectMapper;

    /**
     * Returns advice for a completed session.
     * Tries the Python service first; falls back to the Java rule-based engine on any error.
     */
    public List<String> getAdvice(AssessmentSession session, String situation, String careerPath) {
        if (enabled) {
            try {
                return callPythonAdvice(session, situation, careerPath);
            } catch (Exception e) {
                log.warn("[AI] Python advice service unavailable ({}), using fallback.", e.getMessage());
            }
        }
        return fallbackAdviceService.generateAdvice(session, situation, careerPath);
    }

    /**
     * Returns suggested category codes for a candidate's onboarding profile.
     * Tries the Python service first; falls back to the Java rule-based engine.
     */
    public List<String> suggestCategories(String situation, String careerPath, String headline, 
                                        String customSituation, String customCareerPath, List<String> availableCodes) {
        if (enabled) {
            try {
                return callPythonSuggest(situation, careerPath, headline, customSituation, customCareerPath, availableCodes);
            } catch (Exception e) {
                log.warn("[AI] Python suggest service unavailable ({}), using fallback.", e.getMessage());
            }
        }
        return fallbackAdviceService.suggestCategories(situation, careerPath, headline, customSituation, customCareerPath, availableCodes);
    }

    // ── Private ────────────────────────────────────────────────────────────

    private List<String> callPythonAdvice(AssessmentSession session, String situation, String careerPath) {
        List<SessionAnswer> answers = sessionAnswerRepository.findDetailBySession(session.getId());

        long easyWrong = answers.stream()
                .filter(a -> !a.isCorrect() && "EASY".equalsIgnoreCase(
                        a.getQuestion().getDifficulty() != null ? a.getQuestion().getDifficulty().name() : ""))
                .count();
        long hardCorrect = answers.stream()
                .filter(a -> a.isCorrect() && "HARD".equalsIgnoreCase(
                        a.getQuestion().getDifficulty() != null ? a.getQuestion().getDifficulty().name() : ""))
                .count();
        long durationMin = 10;
        if (session.getStartedAt() != null && session.getCompletedAt() != null) {
            durationMin = Duration.between(session.getStartedAt(), session.getCompletedAt()).toMinutes();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("score", session.getScorePercent() != null ? session.getScorePercent() : 0);
        body.put("integrity", session.isIntegrityViolation());
        body.put("easyWrong", easyWrong);
        body.put("hardCorrect", hardCorrect);
        body.put("durationMin", durationMin);
        body.put("situation", situation != null ? situation : "");
        body.put("careerPath", careerPath != null ? careerPath : "");
        body.put("categoryTitle", session.getCategory() != null ? session.getCategory().getTitle() : "");

        String json = post("/advice", body);
        Map<String, Object> resp = parseMap(json);
        Object adviceObj = resp.get("advice");
        if (adviceObj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> callPythonSuggest(String situation, String careerPath, String headline,
                                         String customSituation, String customCareerPath, List<String> availableCodes) {
        Map<String, Object> body = new HashMap<>();
        body.put("situation", situation != null ? situation : "");
        body.put("careerPath", careerPath != null ? careerPath : "");
        body.put("headline", headline != null ? headline : "");
        body.put("customSituation", customSituation != null ? customSituation : "");
        body.put("customCareerPath", customCareerPath != null ? customCareerPath : "");
        body.put("availableCodes", availableCodes);

        String json = post("/suggest-categories", body);
        Map<String, Object> resp = parseMap(json);
        Object codes = resp.get("suggestedCodes");
        if (codes instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private String post(String path, Object body) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(2000));
        factory.setReadTimeout(Duration.ofMillis(4000));
        RestClient client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl.trim())
                .build();
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            return client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI service: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
