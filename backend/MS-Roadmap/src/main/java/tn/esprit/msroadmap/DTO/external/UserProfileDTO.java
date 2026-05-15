package tn.esprit.msroadmap.DTO.external;

public record UserProfileDTO(
        Long id,
        String firstName,
        String lastName,
        String email
) {}
