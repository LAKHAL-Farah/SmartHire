package tn.esprit.msprofile.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.stream.Collectors;

@Service
@Slf4j
public class NvidiaAiService {

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

    public NvidiaAiService(
            @Qualifier("nvidiaWebClient") WebClient nvidiaWebClient,
            NvidiaProperties nvidiaProperties,
            ObjectMapper objectMapper
    ) {
        this.nvidiaWebClient = nvidiaWebClient;
        this.nvidiaProperties = nvidiaProperties;
        this.objectMapper = objectMapper;
    }

    public AiResult scoreLinkedInProfile(String rawProfileText) {
        String systemPrompt = """
                You are a LinkedIn profile expert and professional career coach. Your task is
                to evaluate a LinkedIn profile and score it across four key sections.
                SCORING CRITERIA:
                Headline (25% of total):
                100: Specific, role + value proposition + key skills, compelling
                70:  Has role title and some context
                40:  Generic job title only (e.g. "Software Engineer")
                0:   Missing or placeholder
                Summary / About (25% of total):
                100: Achievement-focused, 3+ paragraphs, tells a story, has call to action
                70:  Present and decent but generic
                40:  Very short or just lists responsibilities
                0:   Missing
                Skills (25% of total):
                100: 20+ relevant skills, mix of technical and soft, properly named
                70:  10–19 skills
                40:  Fewer than 10 skills or poorly named
                0:   No skills listed
                Recommendations (25% of total):
                100: 5+ written recommendations from colleagues or managers
                70:  3–4 recommendations
                40:  1–2 recommendations
                0:   No recommendations mentioned
                RULES:

                Return ONLY a valid JSON object. No markdown, no explanation.
                Be strict and honest — do not inflate scores.
                globalScore is the exact average of the four section scores.
                Feedback for each section must be one specific, actionable sentence.
                """;

        String userMessage = "Score this LinkedIn profile:\n" + rawProfileText;
        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.has("globalScore")) {
                throw new ExternalApiException("LinkedIn scoring returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("LinkedIn scoring returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult optimizeLinkedInProfile(
            String rawProfileText,
            String currentHeadline,
            String currentSummary,
            String currentSkills
    ) {
        String systemPrompt = """
                You are an expert LinkedIn profile writer and personal branding specialist.
                Your task is to rewrite a candidate's LinkedIn headline, summary, and skills
                to maximize their professional impact and recruiter appeal.
                RULES:

                NEVER invent experience, achievements, or skills not present in the profile.
                Headline: max 220 characters, specific role + expertise + value proposition.
                Use the format: "Role | Skill1 · Skill2 · Skill3 | What you deliver"
                Summary: 3 paragraphs:

                Para 1: Who you are + years of experience + core specialization
                Para 2: Key achievements and what you've built (use numbers if in original)
                Para 3: What you're looking for / your passion / call to action
                Keep under 2,000 characters total.

                Skills: return 15–25 skills ordered by market demand for this profile type.
                Use full proper names (not abbreviations).
                Return ONLY valid JSON. No markdown, no explanation.
                """;

        String userMessage = "FULL PROFILE TEXT:\n" + safe(rawProfileText)
                + "\nCURRENT HEADLINE: " + safe(currentHeadline)
                + "\nCURRENT SUMMARY: " + safe(currentSummary)
                + "\nCURRENT SKILLS: " + safe(currentSkills)
                + "\nRewrite the headline, summary, and skills for maximum professional impact.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            String optimizedHeadline = pickText(node,
                    "optimizedHeadline", "headline", "improvedHeadline", "targetHeadline", "newHeadline");
            String optimizedSummary = pickText(node,
                    "optimizedSummary", "summary", "about", "improvedSummary", "newSummary");
            List<String> optimizedSkills = pickStringList(node,
                    "optimizedSkills", "skills", "topSkills", "recommendedSkills");

            if (isBlank(optimizedHeadline) || isBlank(optimizedSummary) || optimizedSkills.isEmpty()) {
                throw new ExternalApiException("LinkedIn optimization returned invalid format");
            }

            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("optimizedHeadline", optimizedHeadline.trim());
            normalized.put("optimizedSummary", optimizedSummary.trim());
            ArrayNode skillsNode = normalized.putArray("optimizedSkills");
            for (String skill : optimizedSkills) {
                skillsNode.add(skill);
            }
            normalized.put("optimizationRationale", pickText(node, "optimizationRationale", "rationale", "reasoning"));
            normalized.put("projectedScoreImprovement", pickInt(node,
                    "projectedScoreImprovement", "scoreImprovement", "estimatedScoreGain"));

            cleanedJson = objectMapper.writeValueAsString(normalized);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("LinkedIn optimization returned invalid format", e);
        }
        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult alignLinkedInToJobOffer(
            String currentHeadline,
            String currentSummary,
            String currentSkills,
            String jobDescription,
            List<String> jobKeywords
    ) {
        String systemPrompt = """
                You are an expert LinkedIn profile writer and ATS specialist. Your task is to
                rewrite a candidate's LinkedIn headline, summary, and skills to align with a
                specific job offer — maximizing both ATS keyword matching and recruiter appeal.
                RULES:

                NEVER invent experience or skills not in the current profile.
                Naturally incorporate the provided job keywords where truthful.
                The headline must reflect the target role's title and key requirements.
                The summary should open with relevance to the target role.
                Reorder skills to prioritize those matching the job.
                Return ONLY valid JSON. No markdown, no explanation.
                incorporatedKeywords must only list keywords you actually added.
                """;

        String userMessage = "CURRENT HEADLINE: " + safe(currentHeadline)
                + "\nCURRENT SUMMARY: " + safe(currentSummary)
                + "\nCURRENT SKILLS: " + safe(currentSkills)
                + "\nTARGET JOB DESCRIPTION:\n" + safe(jobDescription)
                + "\nPRIORITY KEYWORDS:\n" + String.join(", ", jobKeywords)
                + "\nAlign the LinkedIn profile to this job offer.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            String alignedHeadline = pickText(node,
                    "alignedHeadline", "headline", "jobAlignedHeadline", "optimizedHeadline", "newHeadline");
            String alignedSummary = pickText(node,
                    "alignedSummary", "summary", "jobAlignedSummary", "optimizedSummary", "newSummary");
            List<String> alignedSkills = pickStringList(node,
                    "alignedSkills", "skills", "jobAlignedSkills", "optimizedSkills");
            List<String> incorporatedKeywords = pickStringList(node,
                    "incorporatedKeywords", "keywords", "matchedKeywords");

            if (isBlank(alignedHeadline) || isBlank(alignedSummary) || alignedSkills.isEmpty()) {
                throw new ExternalApiException("LinkedIn alignment returned invalid format");
            }

            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("alignedHeadline", alignedHeadline.trim());
            normalized.put("alignedSummary", alignedSummary.trim());

            ArrayNode alignedSkillsNode = normalized.putArray("alignedSkills");
            for (String skill : alignedSkills) {
                alignedSkillsNode.add(skill);
            }

            ArrayNode incorporatedKeywordsNode = normalized.putArray("incorporatedKeywords");
            for (String keyword : incorporatedKeywords) {
                incorporatedKeywordsNode.add(keyword);
            }

            normalized.put("alignmentRationale", pickText(node, "alignmentRationale", "rationale", "reasoning"));
            cleanedJson = objectMapper.writeValueAsString(normalized);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("LinkedIn alignment returned invalid format", e);
        }

        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult generateLinkedInTips(String sectionScoresJson, String currentHeadline, String currentSummary) {
        String summaryPreview = safe(currentSummary);
        if (summaryPreview.length() > 500) {
            summaryPreview = summaryPreview.substring(0, 500);
        }

        String systemPrompt = """
                You are a LinkedIn profile coach. Based on a profile's section scores and
                content, generate 3 to 5 specific, actionable tips to improve the profile.
                RULES:

                Each tip must be immediately actionable, not generic.
                Bad: "Improve your headline"
                Good: "Your headline only says 'Software Engineer' — LinkedIn profiles with
                role + specialization + tools get 3x more recruiter views. Try:
                'Senior Software Engineer | Java · Spring Boot · AWS'"
                Return ONLY a valid JSON array. No markdown.

                Categories: HEADLINE | SUMMARY | SKILLS | RECOMMENDATIONS | GENERAL
                Priorities: HIGH | MEDIUM | LOW
                """;

        String userMessage = "Section Scores: " + safe(sectionScoresJson)
                + "\nCurrent Headline: " + safe(currentHeadline)
                + "\nCurrent Summary (first 500 chars): " + summaryPreview
                + "\nGenerate 3–5 specific actionable tips.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isArray()) {
                throw new ExternalApiException("LinkedIn tips generation returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("LinkedIn tips generation returned invalid format", e);
        }

        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult scoreReadme(String repoName, String readmeContent) {
        String systemPrompt = """
                You are a senior software engineer reviewing a GitHub repository README.
                Score the README quality from 0 to 100 across four dimensions.
                SCORING DIMENSIONS:
                Structure (25%):
                100: Clear sections with H2/H3 headers, table of contents for long READMEs
                70:  Some headers but inconsistent organization
                40:  Minimal structure, mostly prose
                0:   No structure, single paragraph or empty
                Clarity (25%):
                100: Purpose clear in first sentence, excellent prose, no jargon without explanation
                70:  Understandable but could be clearer
                40:  Requires effort to understand the purpose
                0:   Confusing or completely missing description
                Completeness (25%):
                100: Installation steps, usage examples with code, API docs or config reference,
                contributing guide, and changelog or version info all present
                70:  Most sections present but one or two missing
                40:  Only description and one or two sections
                0:   Minimal placeholder content
                Professional Quality (25%):
                100: Status badges (build, coverage, version), screenshots or GIF demos,
                license badge and LICENSE file referenced, proper code blocks
                50:  Some professional elements present
                0:   No badges, no visuals, plain text only
                RULES:

                Return ONLY valid JSON. No markdown, no explanation, no code fences.
                If README content is empty, null, or just a placeholder: return score 0.
                fixSuggestions must be specific and immediately actionable — max 3.

                OUTPUT FORMAT:
                {
                "score": 72,
                "structureScore": 80,
                "clarityScore": 75,
                "completenessScore": 65,
                "professionalScore": 68,
                "feedback": "One sentence overall assessment of the README quality",
                "fixSuggestions": [
                "Add an installation section with step-by-step terminal commands",
                "Include at least one code example showing how to use the project",
                "Add a license badge linking to your LICENSE file"
                ]
                }
                """;

        String userMessage;
        if (isBlank(readmeContent)) {
            userMessage = "Repository: " + safe(repoName) + "\n\nThis repository has no README file. Score it 0.";
        } else {
            userMessage = "Repository name: " + safe(repoName)
                    + "\nREADME content:\n" + readmeContent;
        }

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.has("score")) {
                throw new ExternalApiException("GitHub README scoring returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub README scoring returned invalid format", e);
        }

        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult auditRepository(
            String repoName,
            String language,
            String description,
            boolean hasCiCd,
            boolean hasTests,
            int readmeScore,
            int stars,
            String pushedAt
    ) {
        String systemPrompt = """
                You are a senior software engineer and technical recruiter evaluating a GitHub
                repository's quality based on observable signals. You cannot see the source
                code — only metadata and structural signals.
                SCORING FORMULA (must follow exactly):
                README Quality contribution:    readmeScore * 0.40
                CI/CD contribution:            (hasCiCd ? 100 : 0) * 0.20
                Test Coverage contribution:    (hasTests ? 100 : 0) * 0.20
                Activity contribution:         activityScore * 0.20
                Activity score based on pushedAt:
                Within last 30 days:  100
                Within last 90 days:  75
                Within last 6 months: 50
                Within last 1 year:   25
                Older than 1 year:    0
                Unknown:              40
                overallScore = sum of contributions, rounded to nearest integer, clamped 0–100
                RULES:

                Return ONLY valid JSON. No markdown, no explanation.
                codeStructureScore: estimate from signals (0 if no tests and no CI/CD,
                higher if both present and active)
                fixSuggestions: max 3, specific and immediately actionable.
                feedback: one sentence, honest assessment.

                OUTPUT FORMAT:
                {
                "overallScore": 68,
                "codeStructureScore": 55,
                "ciCdScore": 100,
                "testScore": 0,
                "activityScore": 75,
                "feedback": "Active repo with CI/CD but lacking tests reduces overall quality",
                "fixSuggestions": [
                "Add unit tests — no test directory detected in this repository",
                "Improve README score from 45 by adding installation and usage sections",
                "Consider adding code coverage badges once tests are in place"
                ]
                }
                """;

        String userMessage = "Repository: " + safe(repoName)
                + "\nPrimary Language: " + safe(language)
                + "\nDescription: " + safe(description)
                + "\nStars: " + stars
                + "\nHas CI/CD (GitHub Actions detected): " + hasCiCd
                + "\nHas Tests (test directory detected): " + hasTests
                + "\nREADME Score: " + readmeScore + "/100"
                + "\nLast Push: " + safe(pushedAt)
                + "\nAudit this repository using the scoring formula and return the JSON.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.has("overallScore")) {
                throw new ExternalApiException("GitHub repository audit returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub repository audit returned invalid format", e);
        }

        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult generateGitHubProfileFeedback(
            String username,
            int overallScore,
            int repoCount,
            List<String> topLanguages,
            int reposWithCiCd,
            int reposWithTests,
            int avgReadmeScore,
            boolean hasProfileReadme
    ) {
        String systemPrompt = """
                You are a technical recruiter and engineering career coach reviewing a
                developer's GitHub profile. Provide an honest, specific assessment of their
                GitHub presence and actionable recommendations for improvement.
                RULES:

                Return ONLY valid JSON. No markdown, no explanation.
                Be specific — reference the actual numbers in your response.
                strengths and weaknesses: 2–4 items each, specific to the data.
                recommendations: 3–5 items, immediately actionable, ordered by impact.
                recruiterImpression: one sentence from a recruiter's perspective.

                OUTPUT FORMAT:
                {
                "profileSummary": "2–3 sentence honest assessment of this GitHub profile",
                "strengths": [
                "Strong CI/CD adoption — 8 of 12 repos have GitHub Actions",
                "Active contributor with pushes within the last 30 days"
                ],
                "weaknesses": [
                "Only 2 of 12 repos have test directories — signals weak testing culture",
                "Average README score of 35/100 makes repos hard to understand"
                ],
                "recommendations": [
                "Create a profile README (github.com/{username}/{username}) — it appears on your profile page and makes a strong first impression",
                "Add tests to your top 3 starred repos as a priority",
                "Improve the README of your most-starred repo first — high impact, low effort"
                ],
                "recruiterImpression": "Active developer with CI/CD awareness but weak documentation and testing signals reduce confidence in code quality"
                }
                """;

        String languages = topLanguages == null || topLanguages.isEmpty()
                ? "None"
                : topLanguages.stream().filter(item -> item != null && !item.isBlank()).collect(Collectors.joining(", "));

        String userMessage = "GitHub Username: " + safe(username)
                + "\nOverall Profile Score: " + overallScore + "/100"
                + "\nTotal Public Repos: " + repoCount
                + "\nTop Languages: " + languages
                + "\nRepos with CI/CD: " + reposWithCiCd + " of " + repoCount
                + "\nRepos with Tests: " + reposWithTests + " of " + repoCount
                + "\nAverage README Score: " + avgReadmeScore + "/100"
                + "\nHas Profile README (" + safe(username) + "/" + safe(username) + " repo): " + hasProfileReadme
                + "\nProvide a comprehensive GitHub profile assessment.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.has("profileSummary")) {
                throw new ExternalApiException("GitHub profile feedback returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub profile feedback returned invalid format", e);
        }

        return new AiResult(cleanedJson, result.tokensUsed());
    }

    public AiResult generateGitHubTips(String username, int overallScore, String feedbackJson) {
        String systemPrompt = """
                You are a technical career coach. Based on a developer's GitHub profile audit,
                generate 3 to 5 specific actionable improvement tips.
                RULES:

                Each tip must reference specific numbers or repos — not generic advice.
                Bad: "Add more tests to your repos"
                Good: "10 of your 15 repos have no test directory — prioritize adding tests
                to your 3 most-starred repos first, as these get the most recruiter attention"
                Return ONLY a valid JSON array. No markdown, no explanation.

                OUTPUT FORMAT:
                [
                { "tipText": "...", "priority": "HIGH", "category": "TESTS" },
                ...
                ]
                Categories: README | CICD | TESTS | ACTIVITY | PROFILE_README | LANGUAGES | GENERAL
                Priorities: HIGH | MEDIUM | LOW
                Maximum 5 tips. Minimum 3 tips.
                """;

        String userMessage = "GitHub Username: " + safe(username)
                + "\nOverall Score: " + overallScore + "/100"
                + "\nProfile Feedback: " + safe(feedbackJson)
                + "\nGenerate 3–5 specific actionable tips to improve this GitHub profile.";

        AiResult result = call(systemPrompt, userMessage);
        String cleanedJson = extractJson(result.content());

        try {
            JsonNode node = objectMapper.readTree(cleanedJson);
            if (!node.isArray()) {
                throw new ExternalApiException("GitHub tips generation returned invalid format");
            }
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GitHub tips generation returned invalid format", e);
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

            String body = nvidiaWebClient
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(nvidiaProperties.timeoutSeconds()))
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
        } catch (RuntimeException e) {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String pickText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && node.get(fieldName).isTextual()) {
                String value = node.get(fieldName).asText();
                if (!isBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private int pickInt(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && node.get(fieldName).isNumber()) {
                return node.get(fieldName).asInt();
            }
        }
        return 0;
    }

    private List<String> pickStringList(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (!node.has(fieldName) || !node.get(fieldName).isArray()) {
                continue;
            }

            List<String> values = new java.util.ArrayList<>();
            for (JsonNode child : node.get(fieldName)) {
                if (child.isTextual()) {
                    String value = child.asText().trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
