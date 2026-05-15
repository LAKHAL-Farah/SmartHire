package tn.esprit.msuser.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body to enable Face Recognition MFA
 * User sends their face image to register for MFA
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnableFaceRecognitionRequest {

    @NotBlank(message = "Face image (base64) is required")
    private String image;

    /**
     * Optional: Image format (jpeg, png, etc.)
     * Defaults to base64
     */
    private String imageFormat;
}
