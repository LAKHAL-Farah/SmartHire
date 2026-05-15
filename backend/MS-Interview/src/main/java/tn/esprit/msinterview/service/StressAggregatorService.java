package tn.esprit.msinterview.service;

import org.springframework.stereotype.Service;
import tn.esprit.msinterview.dto.StressPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StressAggregatorService {

    private final Map<Long, List<StressPayload>> questionReadings = new ConcurrentHashMap<>();
    private final Map<Long, List<StressPayload>> sessionReadings = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, StressQuestionSummary>> finalizedQuestionSummaries = new ConcurrentHashMap<>();

    public void recordReading(Long sessionId, StressPayload payload) {
        if (sessionId == null || payload == null) {
            return;
        }

        sessionReadings.computeIfAbsent(sessionId, key -> Collections.synchronizedList(new ArrayList<>())).add(payload);

        if (payload.questionId() != null) {
            questionReadings.computeIfAbsent(sessionId, key -> Collections.synchronizedList(new ArrayList<>())).add(payload);
        }
    }

    public StressQuestionSummary finalizeQuestion(Long sessionId, Long questionId) {
        if (sessionId == null || questionId == null) {
            return new StressQuestionSummary(questionId, 0.0, "low", 0, List.of());
        }

        List<StressPayload> readings = snapshot(questionReadings.getOrDefault(sessionId, List.of()));
        if (readings.isEmpty()) {
            StressQuestionSummary empty = new StressQuestionSummary(questionId, 0.0, "low", 0, List.of());
            finalizedQuestionSummaries
                    .computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                    .put(questionId, empty);
            return empty;
        }

        double avg = readings.stream().mapToDouble(StressPayload::stressScore).average().orElse(0);
        String level = levelFromScore(avg);

        List<Double> timeline = new ArrayList<>();
        for (int i = 0; i < readings.size(); i += 5) {
            int end = Math.min(i + 5, readings.size());
            double windowAvg = readings.subList(i, end).stream()
                    .mapToDouble(StressPayload::stressScore)
                    .average()
                    .orElse(0);
            timeline.add(round3(windowAvg));
        }

        StressQuestionSummary summary = new StressQuestionSummary(
                questionId,
                round3(avg),
                level,
                readings.size(),
                timeline
        );

        finalizedQuestionSummaries
                .computeIfAbsent(sessionId, key -> new ConcurrentHashMap<>())
                .put(questionId, summary);

        questionReadings.put(sessionId, Collections.synchronizedList(new ArrayList<>()));
        return summary;
    }

    public Optional<StressQuestionSummary> getFinalizedQuestionSummary(Long sessionId, Long questionId) {
        if (sessionId == null || questionId == null) {
            return Optional.empty();
        }

        Map<Long, StressQuestionSummary> summaries = finalizedQuestionSummaries.get(sessionId);
        if (summaries == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(summaries.get(questionId));
    }

    public StressSessionSummary getSessionSummary(Long sessionId) {
        if (sessionId == null) {
            return new StressSessionSummary(0.0, "low", 0);
        }

        List<StressPayload> readings = snapshot(sessionReadings.getOrDefault(sessionId, List.of()));
        if (readings.isEmpty()) {
            return new StressSessionSummary(0.0, "low", 0);
        }

        double avg = readings.stream().mapToDouble(StressPayload::stressScore).average().orElse(0);
        return new StressSessionSummary(round3(avg), levelFromScore(avg), readings.size());
    }

    public List<StressQuestionSummary> getFinalizedQuestionSummaries(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }

        Map<Long, StressQuestionSummary> summaries = finalizedQuestionSummaries.get(sessionId);
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }

        return summaries.values().stream()
                .sorted(Comparator.comparing(
                        StressQuestionSummary::questionId,
                        Comparator.nullsLast(Long::compareTo)
                ))
                .toList();
    }

    public void clearSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }

        sessionReadings.remove(sessionId);
        questionReadings.remove(sessionId);
        finalizedQuestionSummaries.remove(sessionId);
    }

    private static List<StressPayload> snapshot(List<StressPayload> source) {
        synchronized (source) {
            return new ArrayList<>(source);
        }
    }

    private static String levelFromScore(double score) {
        if (score > 0.6) {
            return "high";
        }
        if (score > 0.35) {
            return "medium";
        }
        return "low";
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record StressQuestionSummary(
            Long questionId,
            double avgScore,
            String level,
            int readingCount,
            List<Double> timeline
    ) {
    }

    public record StressSessionSummary(
            double avgScore,
            String level,
            int totalReadings
    ) {
    }
}
