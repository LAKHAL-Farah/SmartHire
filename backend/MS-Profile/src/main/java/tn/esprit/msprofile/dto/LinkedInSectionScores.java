package tn.esprit.msprofile.dto;

public record LinkedInSectionScores(
        int headline,
        int summary,
        int skills,
        int recommendations,
        int globalScore,
        String headlineFeedback,
        String summaryFeedback,
        String skillsFeedback,
        String recommendationsFeedback

) {
}
