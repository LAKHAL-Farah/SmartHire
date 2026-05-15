package tn.esprit.msuser.exception;

/**
 * Thrown when face recognition is not enabled for a user
 * (e.g., attempting face verification without having registered face first)
 */
public class FaceRecognitionNotEnabledException extends RuntimeException {

    private String userId;

    public FaceRecognitionNotEnabledException(String message) {
        super(message);
    }

    public FaceRecognitionNotEnabledException(String message, String userId) {
        super(message);
        this.userId = userId;
    }

    public FaceRecognitionNotEnabledException(String message, String userId, Throwable cause) {
        super(message, cause);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
