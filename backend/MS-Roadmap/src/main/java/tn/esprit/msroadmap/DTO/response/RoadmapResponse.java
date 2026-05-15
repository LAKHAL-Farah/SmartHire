package tn.esprit.msroadmap.DTO.response;

import tn.esprit.msroadmap.DTO.external.CareerPathDTO;

import java.time.LocalDateTime;
import java.util.List;

public record RoadmapResponse(
        Long id,
        String title,
        String difficulty,
        Integer estimatedWeeks,
        String status,
        Integer totalSteps,
        Integer completedSteps,
        Integer streakDays,
        Integer longestStreak,
        LocalDateTime createdAt,
        List<StepResponse> steps,
        CareerPathDTO careerPath
) {}
