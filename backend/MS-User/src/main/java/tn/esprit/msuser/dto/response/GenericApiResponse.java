package tn.esprit.msuser.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper for all endpoints
 * Provides consistent response format across the application
 *
 * Example JSON:
 * {
 *   "status": "SUCCESS",
 *   "message": "Face recognition enabled successfully",
 *   "data": { ... }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericApiResponse<T> {

    /**
     * Response status: SUCCESS, FACE_REQUIRED, UNAUTHORIZED, ERROR
     */
    private String status;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Response data (can be null)
     */
    private T data;

    /**
     * Timestamp of response
     */
    private Long timestamp;

    /**
     * Constructor for success responses
     */
    public static <T> GenericApiResponse<T> success(String message, T data) {
        return GenericApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> GenericApiResponse<T> success(String message) {
        return GenericApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Constructor for error responses
     */
    public static <T> GenericApiResponse<T> error(String message) {
        return GenericApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> GenericApiResponse<T> error(String message, T data) {
        return GenericApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Constructor for FACE_REQUIRED responses (step 1 of MFA)
     */
    public static <T> GenericApiResponse<T> faceRequired(String message, T data) {
        return GenericApiResponse.<T>builder()
                .status("FACE_REQUIRED")
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Constructor for unauthorized responses
     */
    public static <T> GenericApiResponse<T> unauthorized(String message) {
        return GenericApiResponse.<T>builder()
                .status("UNAUTHORIZED")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
