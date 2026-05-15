package tn.esprit.msassessment.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of MS-User {@code GET /api/v1/profiles/user/{userId}} JSON. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MsProfileApiDto(String firstName, String lastName, String email) {}
