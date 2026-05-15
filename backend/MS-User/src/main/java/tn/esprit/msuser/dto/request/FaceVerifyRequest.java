package tn.esprit.msuser.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for face verification endpoint
 * Contains the temporary token and base64-encoded face image
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceVerifyRequest {

    @NotBlank(message = "Temporary token is required")
    private String tempToken;

    @NotBlank(message = "Face image (base64) is required")
    private String image;

    /**
     * Optional: Image format (jpeg, png, etc.)
     * Defaults to base64
     */
    private String imageFormat;
}
