package tn.esprit.msassessment.integration;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON body sent to MS-User when results are visible to the candidate. Add a matching {@code @PostMapping}
 * in MS-User only when you are ready; until then leave integration disabled.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessmentResultPublishedPayload(
        String eventType,
        String userId,
        Long sessionId,
        Long categoryId,
        Integer scorePercent,
        boolean passed
) {}
