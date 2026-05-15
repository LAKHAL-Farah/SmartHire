package tn.esprit.msroadmap.DTO.response;

import java.time.LocalDateTime;

public record StepProgressResponse(
        Long id,
        Long stepId,
        String status,
        LocalDateTime completeAt
) {}
