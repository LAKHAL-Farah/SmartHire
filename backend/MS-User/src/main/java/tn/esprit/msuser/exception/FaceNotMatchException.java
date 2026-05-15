package tn.esprit.msuser.exception;

/**
 * Thrown when face images don't match during verification
 * Contains confidence score for debugging/logging
 */
public class FaceNotMatchException extends RuntimeException {

    private Double confidenceScore;
    private Double threshold;

    public FaceNotMatchException(String message) {
        super(message);
    }

    public FaceNotMatchException(String message, Double confidenceScore, Double threshold) {
        super(message);
        this.confidenceScore = confidenceScore;
        this.threshold = threshold;
    }

    public FaceNotMatchException(String message, Double confidenceScore, Double threshold, Throwable cause) {
        super(message, cause);
        this.confidenceScore = confidenceScore;
        this.threshold = threshold;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public Double getThreshold() {
        return threshold;
    }
}
