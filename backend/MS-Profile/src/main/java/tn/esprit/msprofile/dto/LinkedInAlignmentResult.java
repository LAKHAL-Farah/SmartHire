package tn.esprit.msprofile.dto;

import java.util.List;

public record LinkedInAlignmentResult(
        String alignedHeadline,
        String alignedSummary,
        List<String> alignedSkills,
        List<String> incorporatedKeywords,
        String alignmentRationale

) {
}
