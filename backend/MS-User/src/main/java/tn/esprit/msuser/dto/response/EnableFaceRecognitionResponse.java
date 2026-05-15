package tn.esprit.msuser.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response after enabling Face Recognition MFA for user
 *
 * Example JSON:
 * {
 *   "status": "SUCCESS",
 *   "message": "Face recognition enabled successfully",
 *   "faceRecognitionEnabled": true,
 *   "userId": "550e8400-e29b-41d4-a716-446655440000",
 *   "email": "user@example.com",
 *   "faceEmbeddingId": "emb_123456789"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnableFaceRecognitionResponse {

    /**
     * Response status
     */
    private String status;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Whether face recognition is now enabled for the user
     */
    private Boolean faceRecognitionEnabled;

    /**
     * User ID
     */
    private String userId;

    /**
     * User email
     */
    private String email;

    /**
     * Face embedding ID (identifier used to store and retrieve face embeddings)
     * NOT the raw embedding data, just an ID reference
     */
    private String faceEmbeddingId;

    /**
     * Number of attempts made to capture the face
     */
    private Integer captureAttempts;

    /**
     * Final confidence score of the captured face
     */
    private Double confidenceScore;

    /**
     * Factory method for successful face registration
     */
    public static EnableFaceRecognitionResponse createSuccess(String userId, String email, 
                                                              String faceEmbeddingId, Double confidenceScore) {
        return EnableFaceRecognitionResponse.builder()
                .status("SUCCESS")
                .message("Face recognition enabled successfully")
                .faceRecognitionEnabled(true)
                .userId(userId)
                .email(email)
                .faceEmbeddingId(faceEmbeddingId)
                .confidenceScore(confidenceScore)
                .build();
    }

    /**
     * Factory method for successful update
     */
    public static EnableFaceRecognitionResponse createUpdateSuccess(String userId, String email) {
        return EnableFaceRecognitionResponse.builder()
                .status("SUCCESS")
                .message("Face recognition updated successfully")
                .faceRecognitionEnabled(true)
                .userId(userId)
                .email(email)
                .build();
    }
}
