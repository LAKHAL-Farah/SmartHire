package tn.esprit.msuser.exception;

/**
 * Thrown when face recognition service fails during verification or registration
 */
public class FaceRecognitionException extends RuntimeException {

    private String errorCode;
    private Double confidenceScore;

    public FaceRecognitionException(String message) {
        super(message);
        this.errorCode = "FACE_RECOGNITION_ERROR";
    }

    public FaceRecognitionException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "FACE_RECOGNITION_ERROR";
    }

    public FaceRecognitionException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public FaceRecognitionException(String message, String errorCode, Double confidenceScore) {
        super(message);
        this.errorCode = errorCode;
        this.confidenceScore = confidenceScore;
    }

    public FaceRecognitionException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }
}
