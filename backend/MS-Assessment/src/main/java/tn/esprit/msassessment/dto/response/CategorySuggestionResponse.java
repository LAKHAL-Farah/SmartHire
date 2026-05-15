package tn.esprit.msassessment.dto.response;

import tn.esprit.msassessment.dto.admin.CategoryAdminResponse;

import java.util.List;

/**
 * AI-suggested categories for a candidate based on their onboarding profile.
 * Returned by {@code GET /admin/assignments/{userId}/suggest-categories}.
 */
public record CategorySuggestionResponse(
        String userId,
        String situation,
        String careerPath,
        /** Ordered list of suggested category codes (best match first). */
        List<String> suggestedCategoryCodes,
        /** Full category rows matching the suggestions (same order). */
        List<CategoryAdminResponse> suggestedCategories
) {}
