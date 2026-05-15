package tn.esprit.msroadmap.DTO.request;

import java.util.List;

public record RoadmapRequest(
        Long userId,
        Long careerPathId, // The ID from SVC2
        String title,
        String difficulty,
        Integer estimatedWeeks,
        List<StepRequest> steps
) {}