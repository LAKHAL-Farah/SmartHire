package tn.esprit.msprofile.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tn.esprit.msprofile.config.properties.NvidiaProperties;
import tn.esprit.msprofile.exception.ExternalApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OpenAiService {

    private final WebClient nvidiaWebClient;
    private final NvidiaProperties nvidiaProperties;
    private final ObjectMapper objectMapper;

    record NimMessage(String role, String content) {
    }

    record NimRequest(
            String model,
            List<NimMessage> messages,
            double temperature,
            @JsonProperty("top_p") double topP,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream
    ) {
    }

    record NimChoice(int index, NimMessage message,
                     @JsonProperty("finish_reason") String finishReason) {
    }

    record NimUsage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }

    record NimResponse(String id, List<NimChoice> choices, NimUsage usage) {
    }

    public record AiResult(String content, int tokensUsed) {
    }

    public OpenAiService(
            @Qualifier("nvidiaWebClient") WebClient nvidiaWebClient,
            NvidiaProperties nvidiaProperties,
            ObjectMapper objectMapper
    ) {
        this.nvidiaWebClient = nvidiaWebClient;
        this.nvidiaProperties = nvidiaProperties;
        this.objectMapper = objectMapper;
    }

    public AiResult extractStructuredCvContent(String rawCvText) {
        String systemPrompt = """
                You are an expert CV parser. Your task is to extract structured information
                from the raw text of a candidate's CV and return it as a valid JSON object.
                RULES:

                Return ONLY a valid JSON object. No markdown, no code fences, no explanation.
                If a field is not found in the CV, use null for strings or [] for arrays.
                Do not invent or guess information not present in the CV text.
                Normalize dates to "YYYY" or "YYYY–YYYY" format where possible.
                Extract ALL skills mentioned anywhere in the CV (technical skills, tools,
                frameworks, soft skills, certifications).
                Preserve experience and education entries as completely as possible.
                For experience.description, return concise bullet lines inside one string,
                each bullet starting with "- ".
                Do not output empty arrays if information exists in the CV text.

                OUTPUT FORMAT (return exactly this structure):
                {
                "name": "Full name of the candidate",
                "email": "email address or null",
                "phone": "phone number or null",
                "summary": "professional summary or first paragraph describing the candidate",
                "skills": ["skill1", "skill2", "skill3"],
                "experience": [
                {
                "title": "job title",
                "company": "company name",
                "duration": "start – end dates",
                "description": "responsibilities and achievements"
                }
                ],
                "education": [
                {
                "degree": "degree name",
                "institution": "university or school name",
                "year": "graduation year"
                }
                ]
                }
                """;

        String userMessage = "Parse the following CV text and return the structured JSON:\n" + rawCvText;
        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            objectMapper.readTree(cleanedJson);
        } catch (Exception e) {
            throw new ExternalApiException("CV parser returned invalid JSON", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult extractJobKeywords(String jobDescription) {
        String systemPrompt = """
                You are an expert technical recruiter and ATS (Applicant Tracking System)
                specialist. Your task is to extract the most important keywords from a job
                description that a candidate's CV must contain to pass ATS screening.
                RULES:

                Return ONLY a valid JSON array of strings. No markdown, no explanation.
                Extract technical skills, programming languages, frameworks, tools, platforms,
                methodologies, and certifications.
                Include both explicit requirements and strongly implied ones.
                Order keywords by importance (most critical first).
                Normalize to standard forms: "JavaScript" not "JS", "Kubernetes" not "K8s".
                Include between 10 and 25 keywords.
                Do not include generic soft skills like "communication" or "teamwork" unless
                explicitly listed as requirements.

                OUTPUT FORMAT:
                ["keyword1", "keyword2", "keyword3", ...]
                """;
        String userMessage = "Extract the ATS keywords from this job description:\n" + jobDescription;
        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isArray()) {
                throw new ExternalApiException("Keyword extraction returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Keyword extraction returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult tailorCvContent(String cvJson, String jobDescription, List<String> jobKeywords) {
        String systemPrompt = """
                You are an expert CV writer and ATS optimization specialist with deep knowledge
                of applicant tracking systems and modern recruitment practices.
                Your task is to rewrite a candidate's CV to maximize its ATS score for a specific
                job description, while maintaining complete factual accuracy.
                CRITICAL RULES — you must follow all of these:

                NEVER invent, fabricate, or exaggerate any experience, skill, education,
                or achievement. Only use information present in the original CV.
                Keep ALL top-level fields exactly: name, email, phone, summary, skills, experience, education.
                Do not drop sections or reduce section completeness.
                Naturally incorporate the provided keywords into existing content where
                truthful and relevant — do not keyword-stuff.
                Rewrite the summary to directly target the specific role and company context.
                Keep summary concise (3-5 lines, max ~600 characters) and high impact.
                Reorder skills to prioritize those matching the job requirements.
                Enhance experience descriptions to highlight achievements relevant to the role,
                using strong action verbs and quantified results where they already exist.
                For each experience.description, output 2-5 bullet lines inside a single string,
                each bullet starting with "- ".
                Preserve the exact same JSON structure as the input — same fields, same schema.
                Return ONLY the updated CV as a valid JSON object. No markdown, no explanation.
                Every rewrite must read naturally — a human recruiter will also read this CV.
                """;

        String userMessage = "ORIGINAL CV (JSON):\n" + cvJson +
                "\nTARGET JOB DESCRIPTION:\n" + jobDescription +
                "\nPRIORITY KEYWORDS TO INCORPORATE:\n" + String.join(", ", jobKeywords) +
                "\nRewrite the CV to maximize ATS keyword matching for this role while keeping\n" +
                "all information factually accurate. Return only the updated JSON object.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isObject()) {
                throw new ExternalApiException("CV tailoring returned invalid format");
            }
            if (!node.has("name") || !node.has("skills") || !node.has("experience")) {
                throw new ExternalApiException("Tailored CV is missing required fields");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("CV tailoring returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult computeAtsScore(String cvJson, String jobDescription, List<String> jobKeywords) {
        String systemPrompt = """
                You are an ATS (Applicant Tracking System) scoring engine. Your task is to
                evaluate how well a candidate's CV matches a job description and return a
                detailed scoring analysis.
                Evaluate across these dimensions:

                Keyword Match (40%): How many of the required keywords appear in the CV?
                Experience Relevance (30%): Does the candidate's experience align with
                the role's requirements and seniority level?
                Skills Alignment (20%): Do the candidate's skills cover the technical
                requirements of the role?
                Education & Certifications (10%): Does the candidate meet educational
                requirements?

                RULES:

                Return ONLY a valid JSON object. No markdown, no explanation.
                All scores are integers from 0 to 100.
                matchedKeywords must only list keywords that genuinely appear in the CV.
                missingKeywords must only list keywords from the provided list not in the CV.
                Be accurate and strict — do not inflate scores.

                OUTPUT FORMAT:
                {
                "overallScore": 75,
                "breakdown": {
                "keywordMatch": 80,
                "experienceRelevance": 70,
                "skillsAlignment": 75,
                "educationCertifications": 80
                },
                "matchedKeywords": ["Java", "Spring Boot", "Docker"],
                "missingKeywords": ["Kubernetes", "Kafka"],
                "strengthSummary": "One sentence describing the candidate's main strengths
                for this role",
                "improvementSuggestions": [
                "Specific actionable suggestion 1",
                "Specific actionable suggestion 2"
                ]
                }
                """;

        String userMessage = "CV (JSON):\n" + cvJson +
                "\nJOB DESCRIPTION:\n" + jobDescription +
                "\nREQUIRED KEYWORDS:\n" + String.join(", ", jobKeywords) +
                "\nScore this CV against the job description and return the JSON analysis.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isObject()) {
                throw new ExternalApiException("ATS scoring returned invalid format");
            }
            if (!node.has("overallScore") || !node.has("breakdown")) {
                throw new ExternalApiException("ATS scoring returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("ATS scoring returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult optimizeCvGenerically(String cvJson) {
        String systemPrompt = """
                You are an expert CV writer and career coach with deep knowledge of what makes
                a CV stand out to both ATS systems and human recruiters across all industries.
                Your task is to improve a candidate's CV according to universal best practices,
                making it stronger, clearer, and more impactful — without targeting any specific
                job offer.

                RULES:

                NEVER invent, fabricate, or exaggerate any experience, skill, or achievement.
                Keep ALL top-level fields exactly: name, email, phone, summary, skills, experience, education.
                Do not drop sections or reduce section completeness.
                Strengthen the professional summary: make it punchy, specific, and achievement-focused.
                Keep summary concise (3-5 lines, max ~600 characters).
                Quantify achievements wherever the original hints at measurable outcomes only if
                the original implies a quantifiable result.
                Use strong action verbs at the start of each experience bullet.
                For each experience.description, output 2-5 bullet lines inside a single string,
                each bullet starting with "- ".
                Ensure all skills are properly named (full names, not abbreviations).
                Remove filler phrases like "responsible for", "worked on", "helped with".
                Reorder skills to lead with the most impressive and industry-standard ones.
                Preserve the exact same JSON structure as the input.
                Return ONLY the updated CV as a valid JSON object. No markdown, no explanation.
                """;

        String userMessage = "Improve this CV according to universal best practices.\n"
                + "Return only the updated JSON object with the same structure.\nCV:\n" + cvJson;

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isObject() || !node.has("name") || !node.has("skills") || !node.has("experience")) {
                throw new ExternalApiException("Generic CV optimization returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Generic CV optimization returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult generateCvTips(String cvJson, String atsAnalysisJson) {
        String systemPrompt = """
                You are a career coach and CV specialist. Based on a candidate's CV and their
                ATS analysis results, generate 3 to 5 specific, actionable improvement tips.

                RULES:

                Each tip must be concrete and immediately actionable — not generic advice.
                Assign a priority: HIGH for critical gaps, MEDIUM for improvements, LOW for polish suggestions.
                Return ONLY a valid JSON array. No markdown, no explanation.

                OUTPUT FORMAT:
                [
                  {
                    "tipText": "Specific actionable tip text here",
                    "priority": "HIGH",
                    "category": "SKILLS"
                  }
                ]

                Categories: SUMMARY | SKILLS | EXPERIENCE | EDUCATION | KEYWORDS | FORMATTING
                Priorities: HIGH | MEDIUM | LOW
                Maximum 5 tips. Minimum 3 tips.
                """;

        String userMessage = "CV (JSON):\n" + cvJson + "\nATS Analysis:\n" + atsAnalysisJson
                + "\nGenerate 3–5 specific, actionable improvement tips for this CV.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isArray()) {
                throw new ExternalApiException("Tip generation returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Tip generation returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult scoreCompletenessWithAi(String cvJson) {
        String systemPrompt = """
                You are a professional CV reviewer and career coach. Evaluate the structural
                completeness and quality of a candidate's CV.

                SCORING CRITERIA:

                Contact Info (10%): name, email, phone all present and properly formatted
                Professional Summary (20%): present, specific, achievement-focused, 3–5 lines
                Skills (20%): present, well-populated (8+ skills), properly named
                Experience (30%): present, quantified achievements, strong action verbs,
                clear dates and company names
                Education (20%): present, degree and institution clearly stated

                RULES:

                Return ONLY a valid JSON object. No markdown, no explanation.
                Be strict — a vague summary scores 40, a missing one scores 0.
                missingElements: list of section names or elements completely absent
                weakElements: list of specific improvement areas with brief descriptions
                """;

        String userMessage = "Evaluate the completeness and quality of this CV:\n" + cvJson;

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isObject() || !node.has("overallScore")) {
                throw new ExternalApiException("Completeness scoring returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Completeness scoring returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    private AiResult call(String systemPrompt, String userMessage) {
        Instant started = Instant.now();
        String requestJson = null;

        try {
            NimRequest request = new NimRequest(
                    nvidiaProperties.model(),
                    List.of(new NimMessage("system", systemPrompt), new NimMessage("user", userMessage)),
                    nvidiaProperties.temperature(),
                    nvidiaProperties.topP(),
                    nvidiaProperties.maxTokens(),
                    false
            );

            requestJson = objectMapper.writeValueAsString(request);
            log.info("Calling NVIDIA NIM: method={}, model={}", "call", nvidiaProperties.model());
            log.debug("NIM request body: {}", requestJson);

                int timeoutSec = Math.max(10, nvidiaProperties.timeoutSeconds());
                String body = nvidiaWebClient
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .block();

            if (body == null || body.isBlank()) {
                throw new ExternalApiException("Empty response from NVIDIA NIM API");
            }

            NimResponse response = objectMapper.readValue(body, NimResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new ExternalApiException("Empty response from NVIDIA NIM API");
            }

            String content = response.choices().get(0).message().content();
            int tokensUsed = response.usage() != null ? response.usage().totalTokens() : 0;
            long durationMs = Duration.between(started, Instant.now()).toMillis();

            log.info("NIM response received: tokensUsed={}, durationMs={}", tokensUsed, durationMs);
            log.debug("NVIDIA NIM durationMs={}", durationMs);
            return new AiResult(content, tokensUsed);
        } catch (WebClientResponseException e) {
            log.error("NIM API error: status={}, method={}, error={}", e.getStatusCode().value(), "call", e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401) {
                throw new ExternalApiException("Invalid NVIDIA API key — set NVIDIA_NIM_API_KEY environment variable", e);
            }
            if (e.getStatusCode().value() == 429) {
                throw new ExternalApiException("NVIDIA NIM rate limit exceeded — retry after a moment", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new ExternalApiException("NVIDIA NIM service unavailable", e);
            }
            throw new ExternalApiException("AI call failed: " + e.getStatusCode().value(), e);
        } catch (ExternalApiException e) {
            throw e;
        } catch (java.lang.RuntimeException e) {
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                throw new ExternalApiException("AI call timed out after " + nvidiaProperties.timeoutSeconds() + " seconds", e);
            }
            throw new ExternalApiException("NVIDIA NIM service unavailable", e);
        } catch (Exception e) {
            throw new ExternalApiException("NVIDIA NIM service unavailable", e);
        }
    }

    String extractJson(String raw) {
        if (raw == null) {
            return null;
        }

        String cleaned = raw.trim();
        String original = cleaned;
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }

        int firstBrace = cleaned.indexOf('{');
        int firstBracket = cleaned.indexOf('[');
        int start = -1;
        if (firstBrace >= 0 && firstBracket >= 0) {
            start = Math.min(firstBrace, firstBracket);
        } else if (firstBrace >= 0) {
            start = firstBrace;
        } else if (firstBracket >= 0) {
            start = firstBracket;
        }

        int lastBrace = cleaned.lastIndexOf('}');
        int lastBracket = cleaned.lastIndexOf(']');
        int end = Math.max(lastBrace, lastBracket);

        if (start >= 0 && end > start) {
            String candidate = cleaned.substring(start, end + 1).trim();
            if (!candidate.equals(original)) {
                String preview = original.length() > 64 ? original.substring(0, 64) : original;
                log.warn("NIM response required JSON cleanup: raw started with '{}'", preview);
            }
            return candidate;
        }

        return raw;
    }
}
