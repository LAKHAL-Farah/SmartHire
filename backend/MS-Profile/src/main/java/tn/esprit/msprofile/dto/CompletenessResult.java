package tn.esprit.msprofile.dto;

import java.util.List;
import java.util.Map;

public record CompletenessResult(
        int overallScore,
        Map<String, SectionCompleteness> sections,
        List<String> missingElements,
        List<String> weakElements,
        String verdict
) {
    public record SectionCompleteness(
            boolean present,
            int score,
            String feedback
    ) {
    }
}
