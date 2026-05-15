package tn.esprit.msroadmap.DTO.response;

public record StepResponse(
        Long id,
        Integer stepOrder,
        String title,
        String objective,
        Integer estimatedDays,
        String status
) {}