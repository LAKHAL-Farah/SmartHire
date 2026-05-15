package tn.esprit.msroadmap.DTO.request;

public record StepRequest(
        String title,
        Integer stepOrder,
        Integer estimatedDays
) {}
