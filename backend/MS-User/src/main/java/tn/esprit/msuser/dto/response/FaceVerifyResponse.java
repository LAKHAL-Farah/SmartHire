package tn.esprit.msuser.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Response after face verification
 * On success: contains JWT token for full authentication
 * On failure: contains error message
 *
 * Example success JSON:
 * {
 *   "status": "SUCCESS",
 *   "message": "Face verified successfully",
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "userId": "550e8400-e29b-41d4-a716-446655440000",
 *   "email": "user@example.com",
 *   "userName": "John Doe"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FaceVerifyResponse {

    /**
     * Response status: SUCCESS or UNAUTHORIZED
     */
    private String status;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Full JWT token for authenticated access (only on success)
     */
    private String token;

    /**
     * User UUID (only on success)
     */
    private UUID userId;

    /**
     * User email (only on success)
     */
    private String email;

    /**
     * User full name (only on success)
     */
    private String userName;

    /**
     * User role (only on success)
     */
    private String roles;

    /**
     * Face matching confidence score (0.0 to 1.0)
     * Useful for debugging/logging purposes
     */
    private Double confidenceScore;

    /**
     * Timestamp of verification
     */
    private Long timestamp;

    /**
     * Factory method for successful face verification
     */
    public static FaceVerifyResponse createSuccess(String token, UUID userId, String email, String userName, 
                                                    String roles, Double confidenceScore) {
        return FaceVerifyResponse.builder()
                .status("SUCCESS")
                .message("Face verified successfully")
                .token(token)
                .userId(userId)
                .email(email)
                .userName(userName)
                .roles(roles)
                .confidenceScore(confidenceScore)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Factory method for failed face verification
     */
    public static FaceVerifyResponse createUnauthorized(String message, Double confidenceScore) {
        return FaceVerifyResponse.builder()
                .status("UNAUTHORIZED")
                .message(message)
                .confidenceScore(confidenceScore)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
