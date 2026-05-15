package tn.esprit.msuser.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response after first login step when Face Recognition MFA is enabled
 * Contains temporary token for the next face verification step
 *
 * Example JSON:
 * {
 *   "status": "FACE_REQUIRED",
 *   "message": "Face recognition required",
 *   "tempToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "expiresIn": 45,
 *   "userId": "550e8400-e29b-41d4-a716-446655440000"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FaceLoginResponse {

    /**
     * Status of authentication step
     */
    private String status;

    /**
     * User-friendly message
     */
    private String message;

    /**
     * Temporary JWT token for face verification (valid for 30-60 seconds)
     * This token can ONLY be used with /auth/verify-face endpoint
     */
    private String tempToken;

    /**
     * Token expiration time in seconds
     */
    private Integer expiresIn;

    /**
     * User ID (for reference)
     */
    private String userId;

    /**
     * Email of the user (for user confirmation)
     */
    private String email;

    /**
     * Factory method for creating face login response
     */
    public static FaceLoginResponse createFaceRequired(String tempToken, Integer expiresIn, String userId, String email) {
        return FaceLoginResponse.builder()
                .status("FACE_REQUIRED")
                .message("Face recognition required for complete authentication")
                .tempToken(tempToken)
                .expiresIn(expiresIn)
                .userId(userId)
                .email(email)
                .build();
    }
}
