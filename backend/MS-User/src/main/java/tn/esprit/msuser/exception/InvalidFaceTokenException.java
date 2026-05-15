package tn.esprit.msuser.exception;

/**
 * Thrown when temporary face verification token is invalid, expired, or already used
 */
public class InvalidFaceTokenException extends RuntimeException {

    private String tokenCode;
    private String reason;

    public InvalidFaceTokenException(String message) {
        super(message);
        this.reason = "UNKNOWN";
    }

    public InvalidFaceTokenException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public InvalidFaceTokenException(String message, String tokenCode, String reason) {
        super(message);
        this.tokenCode = tokenCode;
        this.reason = reason;
    }

    public InvalidFaceTokenException(String message, String tokenCode, String reason, Throwable cause) {
        super(message, cause);
        this.tokenCode = tokenCode;
        this.reason = reason;
    }

    public String getTokenCode() {
        return tokenCode;
    }

    public String getReason() {
        return reason;
    }
}
