package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeLinkedInRequest(
        @NotBlank String rawContent,
        String currentHeadline,
        String currentSummary,
        String currentSkills
) {
}
