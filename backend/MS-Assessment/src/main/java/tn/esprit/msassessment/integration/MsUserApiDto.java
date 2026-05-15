package tn.esprit.msassessment.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of MS-User {@code GET /api/v1/users/{id}} JSON for display name resolution. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MsUserApiDto(String email, Profile profile) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(String firstName, String lastName) {}

    public static String formatDisplayName(MsUserApiDto u) {
        if (u == null) {
            return null;
        }
        if (u.profile() != null) {
            String fn = u.profile().firstName() != null ? u.profile().firstName().trim() : "";
            String ln = u.profile().lastName() != null ? u.profile().lastName().trim() : "";
            String combined = (fn + " " + ln).trim();
            if (!combined.isEmpty()) {
                return combined;
            }
        }
        if (u.email() != null && !u.email().isBlank()) {
            return u.email().trim();
        }
        return null;
    }
}
