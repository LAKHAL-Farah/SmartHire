package tn.esprit.msroadmap.DTO.request;

public record StepResourceDto(
        String type,
        String provider,
        String title,
        String url,
        Double rating,
        Double durationHours,
        Double price,
        Boolean isFree,
        String externalId
) {}
