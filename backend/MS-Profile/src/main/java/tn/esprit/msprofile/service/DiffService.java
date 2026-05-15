package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msprofile.dto.DiffResult;
import tn.esprit.msprofile.dto.DiffResult.DiffType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiffService {

    private final ObjectMapper objectMapper;

    public DiffResult computeDiff(String originalCvJson, String tailoredCvJson) {
        JsonNode originalNode = readTree(originalCvJson);
        JsonNode tailoredNode = readTree(tailoredCvJson);

        List<DiffResult.SectionDiff> sections = new ArrayList<>();
        sections.add(compareSummary(originalNode, tailoredNode));
        sections.add(compareSkills(originalNode, tailoredNode));
        sections.add(compareExperience(originalNode, tailoredNode));
        sections.add(compareEducation(originalNode, tailoredNode));

        int totalChanges = (int) sections.stream().filter(section -> section.diffType() != DiffType.UNCHANGED).count();
        int keywordsAdded = sections.stream().mapToInt(section -> section.addedKeywords() == null ? 0 : section.addedKeywords().size()).sum();
        int keywordsRemoved = sections.stream().mapToInt(section -> section.removedKeywords() == null ? 0 : section.removedKeywords().size()).sum();
        int sentencesRewritten = sections.stream().mapToInt(section -> estimateSentenceChanges(section.originalContent(), section.revisedContent())).sum();

        return new DiffResult(sections, totalChanges, keywordsAdded, keywordsRemoved, sentencesRewritten);
    }

    public String serializeDiff(DiffResult diff) {
        try {
            return objectMapper.writeValueAsString(diff);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize diff result", e);
        }
    }

    /**
     * Produces a compact JSON snapshot compatible with frontend expectations.
     * Example shape: { summary: [...], experience: [...], skills: [...] }
     */
    public String serializeCompactSnapshot(DiffResult diff) {
        try {
            if (diff == null || diff.sections() == null) {
                return objectMapper.writeValueAsString(Map.of("summary", List.of(), "experience", List.of(), "skills", List.of()));
            }
            List<String> summary = new ArrayList<>();
            List<String> experience = new ArrayList<>();
            List<String> skills = new ArrayList<>();

            for (DiffResult.SectionDiff section : diff.sections()) {
                String name = section.sectionName();
                if ("summary".equalsIgnoreCase(name)) {
                    if (section.diffType() != DiffType.UNCHANGED) {
                        summary.add(section.changeSummary());
                    }
                } else if ("experience".equalsIgnoreCase(name)) {
                    if (section.diffType() != DiffType.UNCHANGED) {
                        experience.add(section.changeSummary());
                    }
                } else if ("skills".equalsIgnoreCase(name)) {
                    if (section.addedKeywords() != null && !section.addedKeywords().isEmpty()) {
                        skills.addAll(section.addedKeywords());
                    }
                }
            }

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("summary", summary);
            snapshot.put("experience", experience);
            snapshot.put("skills", skills);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize compact diff snapshot", e);
        }
    }

    public DiffResult deserializeDiff(String diffJson) {
        if (diffJson == null || diffJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(diffJson, DiffResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize diff result", e);
        }
    }

    private DiffResult.SectionDiff compareSummary(JsonNode originalNode, JsonNode tailoredNode) {
        String original = textValue(originalNode, "summary");
        String revised = textValue(tailoredNode, "summary");

        if (normalize(original).equals(normalize(revised))) {
            return new DiffResult.SectionDiff("summary", DiffType.UNCHANGED, original, revised, List.of(), List.of(), "Summary unchanged");
        }

        return new DiffResult.SectionDiff(
                "summary",
                DiffType.MODIFIED,
                original,
                revised,
                wordsAdded(original, revised),
                wordsRemoved(original, revised),
                "Summary rewritten to target the role"
        );
    }

    private DiffResult.SectionDiff compareSkills(JsonNode originalNode, JsonNode tailoredNode) {
        List<String> original = stringArray(originalNode.path("skills"));
        List<String> revised = stringArray(tailoredNode.path("skills"));

        Map<String, String> originalMap = normalizeIndex(original);
        Map<String, String> revisedMap = normalizeIndex(revised);

        List<String> added = revisedMap.entrySet().stream()
                .filter(entry -> !originalMap.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<String> removed = originalMap.entrySet().stream()
                .filter(entry -> !revisedMap.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        DiffType type = added.isEmpty() && removed.isEmpty() ? DiffType.UNCHANGED : DiffType.MODIFIED;
        return new DiffResult.SectionDiff(
                "skills",
                type,
                joinList(original),
                joinList(revised),
                added,
                removed,
                type == DiffType.UNCHANGED ? "Skills unchanged" : added.size() + " skills added, " + removed.size() + " skills removed"
        );
    }

    private DiffResult.SectionDiff compareExperience(JsonNode originalNode, JsonNode tailoredNode) {
        List<JsonNode> original = arrayNodes(originalNode.path("experience"));
        List<JsonNode> revised = arrayNodes(tailoredNode.path("experience"));

        Map<String, JsonNode> originalMap = original.stream().collect(Collectors.toMap(this::experienceKey, node -> node, (left, right) -> left, LinkedHashMap::new));
        Map<String, JsonNode> revisedMap = revised.stream().collect(Collectors.toMap(this::experienceKey, node -> node, (left, right) -> left, LinkedHashMap::new));

        boolean anyAdded = false;
        boolean anyRemoved = false;
        boolean anyModified = false;
        List<String> addedKeywords = new ArrayList<>();
        List<String> removedKeywords = new ArrayList<>();
        String summary = "Experience unchanged";

        for (Map.Entry<String, JsonNode> entry : revisedMap.entrySet()) {
            JsonNode originalEntry = originalMap.get(entry.getKey());
            JsonNode revisedEntry = entry.getValue();
            if (originalEntry == null) {
                anyAdded = true;
                addedKeywords.addAll(wordsAdded("", revisedEntry.path("description").asText("")));
                summary = experienceSummary(revisedEntry, "added");
                continue;
            }

            String originalDescription = originalEntry.path("description").asText("");
            String revisedDescription = revisedEntry.path("description").asText("");
            if (!normalize(originalDescription).equals(normalize(revisedDescription))) {
                anyModified = true;
                addedKeywords.addAll(wordsAdded(originalDescription, revisedDescription));
                removedKeywords.addAll(wordsRemoved(originalDescription, revisedDescription));
                summary = experienceSummary(revisedEntry, "enhanced");
            }
        }

        for (Map.Entry<String, JsonNode> entry : originalMap.entrySet()) {
            if (!revisedMap.containsKey(entry.getKey())) {
                anyRemoved = true;
                removedKeywords.addAll(wordsRemoved(entry.getValue().path("description").asText(""), ""));
                summary = experienceSummary(entry.getValue(), "removed");
            }
        }

        DiffType type = resolveType(anyAdded, anyRemoved, anyModified);
        return new DiffResult.SectionDiff(
                "experience",
                type,
                serializeArray(original),
                serializeArray(revised),
                unique(addedKeywords),
                unique(removedKeywords),
                type == DiffType.UNCHANGED ? "Experience unchanged" : summary
        );
    }

    private DiffResult.SectionDiff compareEducation(JsonNode originalNode, JsonNode tailoredNode) {
        List<JsonNode> original = arrayNodes(originalNode.path("education"));
        List<JsonNode> revised = arrayNodes(tailoredNode.path("education"));

        Map<String, JsonNode> originalMap = original.stream().collect(Collectors.toMap(this::educationKey, node -> node, (left, right) -> left, LinkedHashMap::new));
        Map<String, JsonNode> revisedMap = revised.stream().collect(Collectors.toMap(this::educationKey, node -> node, (left, right) -> left, LinkedHashMap::new));

        boolean anyAdded = false;
        boolean anyRemoved = false;
        boolean anyModified = false;
        List<String> addedKeywords = new ArrayList<>();
        List<String> removedKeywords = new ArrayList<>();
        String summary = "Education unchanged";

        for (Map.Entry<String, JsonNode> entry : revisedMap.entrySet()) {
            JsonNode originalEntry = originalMap.get(entry.getKey());
            JsonNode revisedEntry = entry.getValue();
            if (originalEntry == null) {
                anyAdded = true;
                summary = educationSummary(revisedEntry, "added");
                continue;
            }

            String originalText = originalEntry.toString();
            String revisedText = revisedEntry.toString();
            if (!normalize(originalText).equals(normalize(revisedText))) {
                anyModified = true;
                addedKeywords.addAll(wordsAdded(originalText, revisedText));
                removedKeywords.addAll(wordsRemoved(originalText, revisedText));
                summary = educationSummary(revisedEntry, "updated");
            }
        }

        for (Map.Entry<String, JsonNode> entry : originalMap.entrySet()) {
            if (!revisedMap.containsKey(entry.getKey())) {
                anyRemoved = true;
                summary = educationSummary(entry.getValue(), "removed");
            }
        }

        DiffType type = resolveType(anyAdded, anyRemoved, anyModified);
        return new DiffResult.SectionDiff(
                "education",
                type,
                serializeArray(original),
                serializeArray(revised),
                unique(addedKeywords),
                unique(removedKeywords),
                type == DiffType.UNCHANGED ? "Education unchanged" : summary
        );
    }

    private DiffType resolveType(boolean anyAdded, boolean anyRemoved, boolean anyModified) {
        if (!anyAdded && !anyRemoved && !anyModified) {
            return DiffType.UNCHANGED;
        }
        if (anyAdded && !anyRemoved && !anyModified) {
            return DiffType.ADDED;
        }
        if (!anyAdded && anyRemoved && !anyModified) {
            return DiffType.REMOVED;
        }
        return DiffType.MODIFIED;
    }

    private int estimateSentenceChanges(String original, String revised) {
        List<String> originalSentences = splitSentences(original);
        List<String> revisedSentences = splitSentences(revised);
        int max = Math.max(originalSentences.size(), revisedSentences.size());
        int changes = 0;
        for (int index = 0; index < max; index++) {
            String left = index < originalSentences.size() ? normalize(originalSentences.get(index)) : "";
            String right = index < revisedSentences.size() ? normalize(revisedSentences.get(index)) : "";
            if (!left.equals(right)) {
                changes++;
            }
        }
        return changes;
    }

    private List<String> splitSentences(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\.\\s+"));
    }

    private String experienceSummary(JsonNode node, String action) {
        String title = node.path("title").asText("");
        String company = node.path("company").asText("");
        if (!title.isBlank() && !company.isBlank()) {
            if ("removed".equals(action)) {
                return "Experience removed for " + title + " at " + company;
            }
            if ("added".equals(action)) {
                return "Experience added for " + title + " at " + company;
            }
            return "Description enhanced for " + title + " at " + company;
        }
        return "Experience section " + action;
    }

    private String educationSummary(JsonNode node, String action) {
        String degree = node.path("degree").asText("");
        String institution = node.path("institution").asText("");
        if (!degree.isBlank() && !institution.isBlank()) {
            return "Education " + action + " for " + degree + " at " + institution;
        }
        return "Education section " + action;
    }

    private List<String> wordsAdded(String original, String revised) {
        Set<String> originalWords = words(original);
        return words(revised).stream().filter(word -> !originalWords.contains(word)).toList();
    }

    private List<String> wordsRemoved(String original, String revised) {
        Set<String> revisedWords = words(revised);
        return words(original).stream().filter(word -> !revisedWords.contains(word)).toList();
    }

    private Set<String> words(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return List.of(value.split("\\s+")).stream()
                .map(this::normalizeWord)
                .filter(word -> !word.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeWord(String word) {
        return word == null ? "" : word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "").trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private List<String> unique(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private Map<String, String> normalizeIndex(List<String> values) {
        Map<String, String> index = new LinkedHashMap<>();
        for (String value : values) {
            String key = normalizeWord(value);
            if (!key.isBlank()) {
                index.putIfAbsent(key, value);
            }
        }
        return index;
    }

    private String joinList(List<String> values) {
        return String.join(", ", values);
    }

    private String serializeArray(List<JsonNode> nodes) {
        try {
            return objectMapper.writeValueAsString(nodes);
        } catch (Exception e) {
            return nodes.toString();
        }
    }

    private String textValue(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private List<JsonNode> arrayNodes(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private String experienceKey(JsonNode node) {
        return normalize(node.path("title").asText("")) + "|" + normalize(node.path("company").asText(""));
    }

    private String educationKey(JsonNode node) {
        return normalize(node.path("degree").asText("")) + "|" + normalize(node.path("institution").asText(""));
    }

    private JsonNode readTree(String json) {
        try {
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CV JSON payload", e);
        }
    }
}
