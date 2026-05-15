package tn.esprit.msinterview.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private static final String QUESTION_BANK_PATH = "seeds/question_bank.json";

    private final InterviewQuestionRepository questionRepo;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void seedDatabase() {
        if (questionRepo.count() > 0) {
            log.info("Database already seeded - skipping.");
            restoreQuestionBankFromSeed();
            return;
        }

        log.info("Seeding question bank from {}...", QUESTION_BANK_PATH);
        List<InterviewQuestion> questions = buildQuestions();
        questionRepo.saveAll(questions);
        log.info("Seeded {} questions.", questionRepo.count());
        restoreQuestionBankFromSeed();
    }

    private void restoreQuestionBankFromSeed() {
        List<InterviewQuestion> canonicalQuestions = buildQuestions();
        if (canonicalQuestions.isEmpty()) {
            log.warn("Question bank reconciliation skipped: no canonical questions found.");
            return;
        }

        Map<String, InterviewQuestion> existingByKey = new HashMap<>();
        for (InterviewQuestion existing : questionRepo.findAll()) {
            String key = questionKey(existing);
            existingByKey.putIfAbsent(key, existing);
        }

        List<InterviewQuestion> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (InterviewQuestion canonical : canonicalQuestions) {
            InterviewQuestion current = existingByKey.get(questionKey(canonical));
            if (current == null) {
                toSave.add(canonical);
                created += 1;
                continue;
            }

            if (syncQuestion(current, canonical)) {
                toSave.add(current);
                updated += 1;
            }
        }

        if (!toSave.isEmpty()) {
            questionRepo.saveAll(toSave);
        }

        log.info("Question bank reconciliation complete. canonical={} created={} updated={}",
                canonicalQuestions.size(), created, updated);
    }

    private void restoreCloudCanvasQuestionMetadata() {
        List<InterviewQuestion> cloudTechnicalQuestions = questionRepo.findByRoleTypeAndType(RoleType.CLOUD, QuestionType.TECHNICAL);
        if (cloudTechnicalQuestions.isEmpty()) {
            return;
        }

        String canonicalMetadata = canonicalCloudCanvasMetadata();
        List<InterviewQuestion> toSave = new ArrayList<>();

        for (InterviewQuestion question : cloudTechnicalQuestions) {
            if (!isCloudCanvasPrompt(question.getQuestionText())) {
                continue;
            }

            if (!safeEquals(question.getMetadata(), canonicalMetadata)) {
                question.setMetadata(canonicalMetadata);
                toSave.add(question);
            }
        }

        if (!toSave.isEmpty()) {
            questionRepo.saveAll(toSave);
            log.info("Cloud canvas metadata restored for {} question(s).", toSave.size());
        }
    }

    private void restoreSoftwareEngineeringCodingQuestions() {
        List<InterviewQuestion> canonicalCodingQuestions = buildQuestions().stream()
                .filter(question -> question.getRoleType() == RoleType.SE)
                .filter(question -> question.getType() == QuestionType.CODING)
                .toList();

        if (canonicalCodingQuestions.isEmpty()) {
            log.warn("No canonical Software Engineering coding questions found in seed file.");
            return;
        }

        Map<String, InterviewQuestion> existingByKey = new HashMap<>();
        for (InterviewQuestion existing : questionRepo.findByRoleTypeAndType(RoleType.SE, QuestionType.CODING)) {
            existingByKey.put(questionKey(existing), existing);
        }

        List<InterviewQuestion> toSave = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (InterviewQuestion canonical : canonicalCodingQuestions) {
            InterviewQuestion current = existingByKey.get(questionKey(canonical));
            if (current == null) {
                toSave.add(canonical);
                created += 1;
                continue;
            }

            if (syncQuestion(current, canonical)) {
                toSave.add(current);
                updated += 1;
            }
        }

        if (!toSave.isEmpty()) {
            questionRepo.saveAll(toSave);
            log.info("Software Engineering coding questions restored. created={} updated={}", created, updated);
        }
    }

    private void backfillMissingAiMlPipelineQuestions() {
        List<InterviewQuestion> existingAiMlPipeline = questionRepo.findByDomainAndIsActiveTrue("ml_pipeline")
                .stream()
                .filter(question -> question.getRoleType() == RoleType.AI)
                .filter(question -> question.getType() == QuestionType.TECHNICAL || question.getType() == QuestionType.SITUATIONAL)
                .toList();

        if (existingAiMlPipeline.size() >= 10) {
            return;
        }

        List<InterviewQuestion> seedAiMlPipeline = buildQuestions().stream()
                .filter(question -> question.getRoleType() == RoleType.AI)
                .filter(question -> question.getType() == QuestionType.TECHNICAL || question.getType() == QuestionType.SITUATIONAL)
                .filter(question -> "ml_pipeline".equalsIgnoreCase(question.getDomain()))
                .toList();

        if (seedAiMlPipeline.isEmpty()) {
            log.warn("No AI ml_pipeline questions found in seed file for backfill.");
            return;
        }

        Set<String> existingKeys = new HashSet<>();
        for (InterviewQuestion question : existingAiMlPipeline) {
            existingKeys.add(questionKey(question));
        }

        List<InterviewQuestion> missing = new ArrayList<>();
        for (InterviewQuestion question : seedAiMlPipeline) {
            if (!existingKeys.contains(questionKey(question))) {
                missing.add(question);
            }
        }

        if (missing.isEmpty()) {
            return;
        }

        questionRepo.saveAll(missing);
        log.info("Backfilled {} missing AI ml_pipeline questions.", missing.size());
    }

    private String questionKey(InterviewQuestion question) {
        String role = question.getRoleType() == null ? "" : question.getRoleType().name();
        String text = question.getQuestionText() == null ? "" : question.getQuestionText().trim().toLowerCase();
        String domain = question.getDomain() == null ? "" : question.getDomain().trim().toLowerCase();
        String category = question.getCategory() == null ? "" : question.getCategory().trim().toLowerCase();
        String type = question.getType() == null ? "" : question.getType().name();
        return role + "|" + type + "|" + domain + "|" + category + "|" + text;
    }

    private List<InterviewQuestion> buildQuestions() {
        try (InputStream input = new ClassPathResource(QUESTION_BANK_PATH).getInputStream()) {
            List<SeedQuestion> seedQuestions = objectMapper.readValue(input, new TypeReference<List<SeedQuestion>>() {
            });

            return seedQuestions.stream()
                    .map(this::toEntity)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load question bank from " + QUESTION_BANK_PATH, e);
        }
    }

    private InterviewQuestion toEntity(SeedQuestion q) {
        RoleType roleType = RoleType.valueOf(normalizeEnum(q.getRoleType()));
        QuestionType questionType = QuestionType.valueOf(normalizeEnum(q.getType()));

        return InterviewQuestion.builder()
                .careerPathId(q.getCareerPathId() != null ? q.getCareerPathId() : 1L)
            .roleType(roleType)
                .questionText(q.getQuestionText())
                .type(questionType)
                .difficulty(DifficultyLevel.valueOf(normalizeEnum(q.getDifficulty())))
                .domain(q.getDomain())
                .category(q.getCategory())
                .expectedPoints(toJsonArray(parseItems(q.getExpectedPoints())))
                .followUps(toJsonArray(parseItems(q.getFollowUps())))
                .hints(toJsonArray(parseItems(q.getHints())))
                .idealAnswer(q.getIdealAnswer())
                .sampleCode(normalizeCodingStarter(questionType, q.getSampleCode()))
                                .metadata(enrichMetadata(roleType, questionType, q.getQuestionText(), q.getMetadata()))
                .isActive(q.isActive())
                .build();
    }

        private boolean syncQuestion(InterviewQuestion current, InterviewQuestion canonical) {
                boolean changed = false;

                if (!safeEquals(current.getCareerPathId(), canonical.getCareerPathId())) {
                        current.setCareerPathId(canonical.getCareerPathId());
                        changed = true;
                }
                if (current.getRoleType() != canonical.getRoleType()) {
                        current.setRoleType(canonical.getRoleType());
                        changed = true;
                }
                if (!safeEquals(current.getQuestionText(), canonical.getQuestionText())) {
                        current.setQuestionText(canonical.getQuestionText());
                        changed = true;
                }
                if (current.getType() != canonical.getType()) {
                        current.setType(canonical.getType());
                        changed = true;
                }
                if (current.getDifficulty() != canonical.getDifficulty()) {
                        current.setDifficulty(canonical.getDifficulty());
                        changed = true;
                }
                if (!safeEquals(current.getDomain(), canonical.getDomain())) {
                        current.setDomain(canonical.getDomain());
                        changed = true;
                }
                if (!safeEquals(current.getCategory(), canonical.getCategory())) {
                        current.setCategory(canonical.getCategory());
                        changed = true;
                }
                if (!safeEquals(current.getExpectedPoints(), canonical.getExpectedPoints())) {
                        current.setExpectedPoints(canonical.getExpectedPoints());
                        changed = true;
                }
                if (!safeEquals(current.getFollowUps(), canonical.getFollowUps())) {
                        current.setFollowUps(canonical.getFollowUps());
                        changed = true;
                }
                if (!safeEquals(current.getHints(), canonical.getHints())) {
                        current.setHints(canonical.getHints());
                        changed = true;
                }
                if (!safeEquals(current.getIdealAnswer(), canonical.getIdealAnswer())) {
                        current.setIdealAnswer(canonical.getIdealAnswer());
                        changed = true;
                }
                if (!safeEquals(current.getSampleCode(), canonical.getSampleCode())) {
                        current.setSampleCode(canonical.getSampleCode());
                        changed = true;
                }
                if (!safeEquals(current.getMetadata(), canonical.getMetadata())) {
                        current.setMetadata(canonical.getMetadata());
                        changed = true;
                }
                if (current.isActive() != canonical.isActive()) {
                        current.setActive(canonical.isActive());
                        changed = true;
                }

                return changed;
        }

        private boolean safeEquals(Object left, Object right) {
                return left == null ? right == null : left.equals(right);
        }

        private String enrichMetadata(RoleType roleType,
                                                                    QuestionType questionType,
                                                                    String questionText,
                                                                    String metadata) {
                if (roleType != RoleType.SE || questionType != QuestionType.CODING) {
                if (roleType == RoleType.CLOUD && questionType == QuestionType.TECHNICAL && isCloudCanvasPrompt(questionText)) {
                    return canonicalCloudCanvasMetadata();
                }
                return metadata;
                }

                String canonicalMetadata = canonicalCodingMetadata(questionText);
                if (canonicalMetadata == null || canonicalMetadata.isBlank()) {
                        return metadata;
                }

                return canonicalMetadata;
        }

        private boolean isCloudCanvasPrompt(String questionText) {
                if (questionText == null || questionText.isBlank()) {
                        return false;
                }

                String normalized = questionText.trim().toLowerCase();
                return normalized.equals("how do you design a cloud architecture for a service that must have 99.99 percent availability?");
        }

        private String canonicalCloudCanvasMetadata() {
                return """
                                {
                                    "mode": "canvas",
                                    "scenario": "Design a cloud architecture for a public API service that targets 99.99% availability, handles traffic spikes, and keeps operations observable.",
                                    "requirements": [
                                        {
                                            "key": "load_balancer",
                                            "label": "Traffic distribution layer (Load Balancer)",
                                            "required": true
                                        },
                                        {
                                            "key": "database",
                                            "label": "Durable database layer",
                                            "required": true
                                        },
                                        {
                                            "key": "auto_scaling",
                                            "label": "Elastic compute scaling (Auto Scaling)",
                                            "required": true
                                        },
                                        {
                                            "key": "monitoring",
                                            "label": "Monitoring and alerting",
                                            "required": true
                                        },
                                        {
                                            "key": "vpc",
                                            "label": "Network isolation boundary (VPC)",
                                            "required": false
                                        }
                                    ],
                                    "evaluationCriteria": [
                                        "Availability and fault tolerance",
                                        "Scalability under variable load",
                                        "Operational observability",
                                        "Security boundary awareness",
                                        "Cost-aware design decisions"
                                    ]
                                }
                                """.trim();
        }

        private String canonicalCodingMetadata(String questionText) {
                if (questionText == null || questionText.isBlank()) {
                        return null;
                }

                String normalized = questionText.trim().toLowerCase();

                if (normalized.equals("given an integer array and a target, return indices of the two numbers that add up to the target.")) {
                        return """
                                        {
                                            "functionSignature": "twoSum(int[] nums, int target)",
                                            "description": "Return zero-based indices of two distinct elements whose sum equals target.",
                                            "constraints": [
                                                "2 <= nums.length <= 100000",
                                                "-1000000000 <= nums[i] <= 1000000000",
                                                "-1000000000 <= target <= 1000000000",
                                                "Exactly one valid answer exists"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "nums = [2,7,11,15], target = 9",
                                                    "output": "[0,1]",
                                                    "explanation": "nums[0] + nums[1] = 2 + 7 = 9"
                                                },
                                                {
                                                    "input": "nums = [3,2,4], target = 6",
                                                    "output": "[1,2]",
                                                    "explanation": "nums[1] + nums[2] = 2 + 4 = 6"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Classic pair",
                                                    "input": "2,7,11,15\\n9",
                                                    "expectedOutput": "[0, 1]",
                                                    "explanation": "Immediate complement match"
                                                },
                                                {
                                                    "label": "Pair appears later",
                                                    "input": "3,2,4\\n6",
                                                    "expectedOutput": "[1, 2]",
                                                    "explanation": "Requires storing previous values"
                                                },
                                                {
                                                    "label": "With negatives",
                                                    "input": "-1,-2,-3,-4,-5\\n-8",
                                                    "expectedOutput": "[2, 4]",
                                                    "explanation": "Negative complements still work"
                                                },
                                                {
                                                    "label": "Duplicate value",
                                                    "input": "3,3\\n6",
                                                    "expectedOutput": "[0, 1]",
                                                    "explanation": "Same value at different indices"
                                                },
                                                {
                                                    "label": "Large values",
                                                    "input": "1000000,-999999,42,-1\\n41",
                                                    "expectedOutput": "[2, 3]",
                                                    "explanation": "Ensure no overflow assumptions"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("determine if a string is a palindrome, ignoring non-alphanumeric characters and case.")) {
                        return """
                                        {
                                            "functionSignature": "isPalindrome(String s)",
                                            "description": "Return true if the normalized string reads the same forward and backward.",
                                            "constraints": [
                                                "0 <= s.length <= 200000",
                                                "s may include punctuation and spaces",
                                                "Comparison is case-insensitive",
                                                "Only letters and digits are considered"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "A man, a plan, a canal: Panama",
                                                    "output": "true",
                                                    "explanation": "Normalized form is amanaplanacanalpanama"
                                                },
                                                {
                                                    "input": "race a car",
                                                    "output": "false",
                                                    "explanation": "Normalized form is raceacar, not symmetric"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Sentence with punctuation",
                                                    "input": "A man, a plan, a canal: Panama",
                                                    "expectedOutput": "true",
                                                    "explanation": "Ignore punctuation and spaces"
                                                },
                                                {
                                                    "label": "Not palindrome",
                                                    "input": "race a car",
                                                    "expectedOutput": "false",
                                                    "explanation": "Mismatch after normalization"
                                                },
                                                {
                                                    "label": "Empty input",
                                                    "input": "",
                                                    "expectedOutput": "true",
                                                    "explanation": "Empty string is palindrome by definition"
                                                },
                                                {
                                                    "label": "Digits and letters",
                                                    "input": "1a2a1",
                                                    "expectedOutput": "true",
                                                    "explanation": "Alphanumeric symmetric"
                                                },
                                                {
                                                    "label": "Case-insensitive",
                                                    "input": "Noon",
                                                    "expectedOutput": "true",
                                                    "explanation": "N and n are equal after lowercase"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("given a string of brackets, determine if the bracket sequence is valid.")) {
                        return """
                                        {
                                            "functionSignature": "isValid(String s)",
                                            "description": "A sequence is valid when every opener is closed in the correct order.",
                                            "constraints": [
                                                "1 <= s.length <= 100000",
                                                "s contains only ()[]{}",
                                                "Closing bracket must match the latest unmatched opener"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "()[]{}",
                                                    "output": "true",
                                                    "explanation": "All bracket pairs are properly matched"
                                                },
                                                {
                                                    "input": "(]",
                                                    "output": "false",
                                                    "explanation": "Expected ')' but found ']'."
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Simple valid",
                                                    "input": "()[]{}",
                                                    "expectedOutput": "true",
                                                    "explanation": "Independent valid groups"
                                                },
                                                {
                                                    "label": "Nested valid",
                                                    "input": "({[]})",
                                                    "expectedOutput": "true",
                                                    "explanation": "Nested open/close order is correct"
                                                },
                                                {
                                                    "label": "Type mismatch",
                                                    "input": "(]",
                                                    "expectedOutput": "false",
                                                    "explanation": "Mismatched closing bracket"
                                                },
                                                {
                                                    "label": "Wrong order",
                                                    "input": "([)]",
                                                    "expectedOutput": "false",
                                                    "explanation": "Crossed pair order"
                                                },
                                                {
                                                    "label": "Unclosed opener",
                                                    "input": "(({{[",
                                                    "expectedOutput": "false",
                                                    "explanation": "Stack not empty at the end"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("find the contiguous subarray with the largest sum.")) {
                        return """
                                        {
                                            "functionSignature": "maxSubArray(int[] nums)",
                                            "description": "Return the maximum possible sum of any contiguous non-empty subarray.",
                                            "constraints": [
                                                "1 <= nums.length <= 100000",
                                                "-100000 <= nums[i] <= 100000",
                                                "Subarray must contain at least one element"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "nums = [-2,1,-3,4,-1,2,1,-5,4]",
                                                    "output": "6",
                                                    "explanation": "Best subarray is [4,-1,2,1]"
                                                },
                                                {
                                                    "input": "nums = [1]",
                                                    "output": "1",
                                                    "explanation": "Single element array"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Kadane classic",
                                                    "input": "-2,1,-3,4,-1,2,1,-5,4",
                                                    "expectedOutput": "6",
                                                    "explanation": "Peak segment appears mid-array"
                                                },
                                                {
                                                    "label": "Single element",
                                                    "input": "1",
                                                    "expectedOutput": "1",
                                                    "explanation": "Only choice"
                                                },
                                                {
                                                    "label": "All negatives",
                                                    "input": "-5,-2,-3",
                                                    "expectedOutput": "-2",
                                                    "explanation": "Pick the least negative element"
                                                },
                                                {
                                                    "label": "All positives",
                                                    "input": "2,3,4",
                                                    "expectedOutput": "9",
                                                    "explanation": "Whole array is optimal"
                                                },
                                                {
                                                    "label": "Zeros and negatives",
                                                    "input": "0,-1,0,-2,0",
                                                    "expectedOutput": "0",
                                                    "explanation": "Zero beats negative sums"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("reverse a singly linked list in place.")) {
                        return """
                                        {
                                            "functionSignature": "reverseList(ListNode head)",
                                            "description": "Reverse pointers of a singly linked list and return the new head.",
                                            "constraints": [
                                                "0 <= number of nodes <= 50000",
                                                "-5000 <= node value <= 5000",
                                                "In-place iterative or recursive solutions are accepted"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "head = [1,2,3,4,5]",
                                                    "output": "[5,4,3,2,1]",
                                                    "explanation": "Reverse next pointers one by one"
                                                },
                                                {
                                                    "input": "head = []",
                                                    "output": "[]",
                                                    "explanation": "Empty list stays empty"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Typical list",
                                                    "input": "[1,2,3,4,5]",
                                                    "expectedOutput": "[5,4,3,2,1]",
                                                    "explanation": "Verify pointer reversal"
                                                },
                                                {
                                                    "label": "Two nodes",
                                                    "input": "[1,2]",
                                                    "expectedOutput": "[2,1]",
                                                    "explanation": "Small non-trivial case"
                                                },
                                                {
                                                    "label": "Single node",
                                                    "input": "[7]",
                                                    "expectedOutput": "[7]",
                                                    "explanation": "Head remains unchanged"
                                                },
                                                {
                                                    "label": "Empty list",
                                                    "input": "[]",
                                                    "expectedOutput": "[]",
                                                    "explanation": "Handle null safely"
                                                },
                                                {
                                                    "label": "Negative values",
                                                    "input": "[-1,-2,-3]",
                                                    "expectedOutput": "[-3,-2,-1]",
                                                    "explanation": "Values do not affect links"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("find all duplicates in an integer array where each element appears once or twice and values are between 1 and n.")) {
                        return """
                                        {
                                            "functionSignature": "findDuplicates(int[] nums)",
                                            "description": "Return all values that appear exactly twice using O(1) extra space.",
                                            "constraints": [
                                                "1 <= nums.length <= 100000",
                                                "1 <= nums[i] <= nums.length",
                                                "Each integer appears once or twice"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "nums = [4,3,2,7,8,2,3,1]",
                                                    "output": "[2,3]",
                                                    "explanation": "2 and 3 each appear twice"
                                                },
                                                {
                                                    "input": "nums = [1,1,2]",
                                                    "output": "[1]",
                                                    "explanation": "Only 1 is duplicated"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Two duplicates",
                                                    "input": "4,3,2,7,8,2,3,1",
                                                    "expectedOutput": "[2, 3]",
                                                    "explanation": "Most common benchmark case"
                                                },
                                                {
                                                    "label": "Single duplicate",
                                                    "input": "1,1,2",
                                                    "expectedOutput": "[1]",
                                                    "explanation": "Only one repeated value"
                                                },
                                                {
                                                    "label": "No duplicates",
                                                    "input": "1,2,3,4",
                                                    "expectedOutput": "[]",
                                                    "explanation": "Every value appears once"
                                                },
                                                {
                                                    "label": "All duplicated pairs",
                                                    "input": "2,2,3,3,4,4",
                                                    "expectedOutput": "[2, 3, 4]",
                                                    "explanation": "Each value appears exactly twice"
                                                },
                                                {
                                                    "label": "Duplicate at ends",
                                                    "input": "5,4,3,2,1,5",
                                                    "expectedOutput": "[5]",
                                                    "explanation": "Check first/last positions"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("implement a stack that supports push, pop, top, and retrieving the minimum element in constant time.")) {
                        return """
                                        {
                                            "functionSignature": "MinStack operations",
                                            "description": "Design push, pop, top, and getMin where each operation runs in O(1).",
                                            "constraints": [
                                                "At most 100000 operations",
                                                "-100000 <= value <= 100000",
                                                "top and getMin are called only when stack is non-empty"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "push 2, push 0, push 3, push 0, getMin, pop, getMin",
                                                    "output": "0 then 0",
                                                    "explanation": "Minimum tracking must survive pops"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Min survives pop",
                                                    "input": "push 2\\npush 0\\npush 3\\npush 0\\ngetMin\\npop\\ngetMin",
                                                    "expectedOutput": "0\\n0",
                                                    "explanation": "Two minimum values stacked"
                                                },
                                                {
                                                    "label": "Top and min",
                                                    "input": "push -2\\npush 0\\npush -3\\ngetMin\\npop\\ntop\\ngetMin",
                                                    "expectedOutput": "-3\\n0\\n-2",
                                                    "explanation": "Classic sequence"
                                                },
                                                {
                                                    "label": "Increasing values",
                                                    "input": "push 1\\npush 2\\npush 3\\ngetMin",
                                                    "expectedOutput": "1",
                                                    "explanation": "Minimum remains first element"
                                                },
                                                {
                                                    "label": "Repeated minimum",
                                                    "input": "push 5\\npush 5\\npush 5\\ngetMin\\npop\\ngetMin",
                                                    "expectedOutput": "5\\n5",
                                                    "explanation": "Equal minimum values"
                                                },
                                                {
                                                    "label": "Negative range",
                                                    "input": "push -1\\npush -2\\npush -3\\ngetMin",
                                                    "expectedOutput": "-3",
                                                    "explanation": "Lower negatives become minimum"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("given a list of intervals, merge all overlapping intervals.")) {
                        return """
                                        {
                                            "functionSignature": "merge(int[][] intervals)",
                                            "description": "Merge overlapping intervals and return a new sorted non-overlapping list.",
                                            "constraints": [
                                                "1 <= intervals.length <= 100000",
                                                "intervals[i].length == 2",
                                                "0 <= start <= end <= 1000000"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "intervals = [[1,3],[2,6],[8,10],[15,18]]",
                                                    "output": "[[1,6],[8,10],[15,18]]",
                                                    "explanation": "[1,3] and [2,6] overlap"
                                                },
                                                {
                                                    "input": "intervals = [[1,4],[4,5]]",
                                                    "output": "[[1,5]]",
                                                    "explanation": "Touching intervals are merged"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Standard overlap",
                                                    "input": "[[1,3],[2,6],[8,10],[15,18]]",
                                                    "expectedOutput": "[[1,6],[8,10],[15,18]]",
                                                    "explanation": "Merge first two intervals"
                                                },
                                                {
                                                    "label": "Touching edges",
                                                    "input": "[[1,4],[4,5]]",
                                                    "expectedOutput": "[[1,5]]",
                                                    "explanation": "Boundary touching counts as overlap"
                                                },
                                                {
                                                    "label": "Already disjoint",
                                                    "input": "[[1,2],[3,4],[5,6]]",
                                                    "expectedOutput": "[[1,2],[3,4],[5,6]]",
                                                    "explanation": "No merges needed"
                                                },
                                                {
                                                    "label": "Contained interval",
                                                    "input": "[[1,10],[2,3],[4,8]]",
                                                    "expectedOutput": "[[1,10]]",
                                                    "explanation": "Inner intervals collapse"
                                                },
                                                {
                                                    "label": "Single interval",
                                                    "input": "[[7,9]]",
                                                    "expectedOutput": "[[7,9]]",
                                                    "explanation": "Trivial case"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("given an m by n grid, find the number of unique paths from the top-left to the bottom-right corner moving only right or down.")) {
                        return """
                                        {
                                            "functionSignature": "uniquePaths(int m, int n)",
                                            "description": "Count all valid right/down paths from (0,0) to (m-1,n-1).",
                                            "constraints": [
                                                "1 <= m, n <= 100",
                                                "Answer fits in 32-bit signed integer for given tests",
                                                "Only moves right or down"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "m = 3, n = 7",
                                                    "output": "28",
                                                    "explanation": "DP or combinatorics both work"
                                                },
                                                {
                                                    "input": "m = 3, n = 2",
                                                    "output": "3",
                                                    "explanation": "Paths: RDD, DRD, DDR"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Classic case",
                                                    "input": "3\\n7",
                                                    "expectedOutput": "28",
                                                    "explanation": "Most common sample"
                                                },
                                                {
                                                    "label": "Small rectangle",
                                                    "input": "3\\n2",
                                                    "expectedOutput": "3",
                                                    "explanation": "Manual counting possible"
                                                },
                                                {
                                                    "label": "Single row",
                                                    "input": "1\\n10",
                                                    "expectedOutput": "1",
                                                    "explanation": "Only move right"
                                                },
                                                {
                                                    "label": "Single column",
                                                    "input": "10\\n1",
                                                    "expectedOutput": "1",
                                                    "explanation": "Only move down"
                                                },
                                                {
                                                    "label": "Square grid",
                                                    "input": "5\\n5",
                                                    "expectedOutput": "70",
                                                    "explanation": "Balanced dimensions"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                if (normalized.equals("implement a least recently used cache with get and put operations both in o(1) time.")) {
                        return """
                                        {
                                            "functionSignature": "LRUCache operations",
                                            "description": "Maintain key-value pairs with capacity bound and LRU eviction policy.",
                                            "constraints": [
                                                "1 <= capacity <= 3000",
                                                "At most 200000 operations",
                                                "Both get and put must run in O(1) average time"
                                            ],
                                            "examples": [
                                                {
                                                    "input": "capacity=2, put(1,1), put(2,2), get(1), put(3,3), get(2)",
                                                    "output": "1 then -1",
                                                    "explanation": "Key 2 is evicted when key 3 is inserted"
                                                }
                                            ],
                                            "tryThese": [
                                                {
                                                    "label": "Evict oldest",
                                                    "input": "2\\nput 1 1\\nput 2 2\\nget 1\\nput 3 3\\nget 2\\nput 4 4\\nget 1\\nget 3\\nget 4",
                                                    "expectedOutput": "1\\n-1\\n-1\\n3\\n4",
                                                    "explanation": "Classic LRU scenario"
                                                },
                                                {
                                                    "label": "Overwrite existing key",
                                                    "input": "2\\nput 2 1\\nput 2 2\\nget 2",
                                                    "expectedOutput": "2",
                                                    "explanation": "Value update should not duplicate key"
                                                },
                                                {
                                                    "label": "Capacity one",
                                                    "input": "1\\nput 2 1\\nget 2\\nput 3 2\\nget 2\\nget 3",
                                                    "expectedOutput": "1\\n-1\\n2",
                                                    "explanation": "Each new key evicts previous"
                                                },
                                                {
                                                    "label": "Read refreshes recency",
                                                    "input": "2\\nput 1 10\\nput 2 20\\nget 1\\nput 3 30\\nget 2\\nget 1",
                                                    "expectedOutput": "10\\n-1\\n10",
                                                    "explanation": "get(1) makes key 1 most recent"
                                                },
                                                {
                                                    "label": "Missing key",
                                                    "input": "3\\nput 1 1\\nget 9",
                                                    "expectedOutput": "-1",
                                                    "explanation": "Unknown key should return -1"
                                                }
                                            ]
                                        }
                                        """.trim();
                }

                return null;
        }

    private String normalizeCodingStarter(QuestionType type, String sampleCode) {
        if (type != QuestionType.CODING) {
            return sampleCode;
        }

        String trimmed = sampleCode == null ? "" : sampleCode.trim();
        if (trimmed.isEmpty()) {
            return "public class Main {\n    public static void main(String[] args) {\n        // Write your solution here\n    }\n}\n";
        }

        String lower = trimmed.toLowerCase();
        boolean hasTopLevelType = lower.startsWith("public class main")
                || lower.startsWith("class main")
                || lower.startsWith("import ")
                || lower.startsWith("package ");
        if (hasTopLevelType) {
            return sampleCode;
        }

        boolean looksLikeMethod = lower.startsWith("public ")
                || lower.startsWith("private ")
                || lower.startsWith("protected ")
                || lower.startsWith("static ");
        if (!looksLikeMethod) {
            return sampleCode;
        }

        StringBuilder wrapped = new StringBuilder();
        wrapped.append("public class Main {\n")
                .append(indentCode(trimmed))
                .append("\n}\n");
        return wrapped.toString();
    }

    private String indentCode(String code) {
        String[] lines = code.split("\\R");
        StringBuilder indented = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                indented.append('\n');
            }
            indented.append("    ").append(lines[i]);
        }
        return indented.toString();
    }

    private String normalizeEnum(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enum value is null or blank in seed file");
        }
        int lastDot = value.lastIndexOf('.');
        return (lastDot >= 0 ? value.substring(lastDot + 1) : value).trim();
    }

    private String toJsonArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize question seed list", e);
        }
    }

    private List<String> parseItems(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof List<?> values) {
            List<String> normalized = new ArrayList<>();
            for (Object value : values) {
                if (value != null) {
                    normalized.add(String.valueOf(value));
                }
            }
            return normalized;
        }
        String single = String.valueOf(rawValue).trim();
        if (single.isEmpty()) {
            return List.of();
        }
        return List.of(single);
    }

    @Data
    private static class SeedQuestion {
        private Long careerPathId;
        private String roleType;
        private String questionText;
        private String type;
        private String difficulty;
        private String domain;
        private String category;
        private Object expectedPoints;
        private Object followUps;
        private Object hints;
        private String idealAnswer;
        private String sampleCode;
        private String metadata;

        @JsonProperty("isActive")
        private boolean active;
    }
}