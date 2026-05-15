package tn.esprit.msassessment.dto.request;

/**
 * When publishing MCQ results: optional internal note (appended to session notes) and optional message shown to the
 * candidate on the results screen.
 */
public record ReleaseSessionResultRequest(String adminNote, String feedbackToCandidate) {

    public ReleaseSessionResultRequest {
        adminNote = (adminNote != null && !adminNote.isBlank()) ? adminNote.trim() : null;
        feedbackToCandidate =
                (feedbackToCandidate != null && !feedbackToCandidate.isBlank())
                        ? feedbackToCandidate.trim()
                        : null;
    }
}
