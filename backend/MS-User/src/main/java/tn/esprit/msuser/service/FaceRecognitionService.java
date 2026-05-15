package tn.esprit.msuser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.msuser.config.FaceRecognitionConfig;
import tn.esprit.msuser.dto.request.FaceVerifyRequest;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.exception.FaceNotMatchException;
import tn.esprit.msuser.exception.FaceRecognitionException;
import tn.esprit.msuser.exception.FaceRecognitionNotEnabledException;
import tn.esprit.msuser.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for Face Recognition MFA operations
 * 
 * Integrates with external Python AI service for face verification and registration
 * 
 * Key responsibilities:
 * 1. Send face verification requests to AI service
 * 2. Send face registration requests to AI service
 * 3. Parse and validate AI service responses
 * 4. Handle timeouts and failures gracefully
 * 5. Manage face embeddings (IDs, not raw data)
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FaceRecognitionService {

    private final RestTemplate faceRecognitionRestTemplate;
    private final FaceRecognitionConfig faceConfig;
    private final UserRepository userRepository;

    /**
     * Verify a user's face for MFA
     * 
     * Flow:
     * 1. Ensure user has face recognition enabled
     * 2. Prepare face images (current + stored embedding reference)
     * 3. Call Python AI service for face comparison
     * 4. Parse confidence score and match result
     * 5. Validate against threshold
     * 
     * @param user The user to verify
     * @param request Face verification request with base64 image
     * @return Confidence score if faces match
     * @throws FaceNotMatchException if faces don't match
     * @throws FaceRecognitionException on service error
     * @throws FaceRecognitionNotEnabledException if user hasn't registered face
     */
    public Double verifyFace(User user, FaceVerifyRequest request) {
        log.debug("Verifying face for user: {}", user.getId());

        // Check if user has face recognition enabled
        if (!user.getFaceRecognitionEnabled() || user.getFaceEmbeddingId() == null) {
            log.warn("Face recognition not enabled for user: {}", user.getId());
            throw new FaceRecognitionNotEnabledException(
                    "Face recognition is not enabled for this user",
                    user.getId().toString()
            );
        }

        try {
            // Prepare request body for AI service
            Map<String, Object> verifyRequest = new HashMap<>();
            verifyRequest.put("image", request.getImage());  // Base64 encoded image
            verifyRequest.put("face_embedding_id", user.getFaceEmbeddingId());  // Reference to stored embedding
            verifyRequest.put("user_id", user.getId().toString());

            // Call Python AI service
            FaceVerificationResponse response = callFaceVerificationService(verifyRequest);

            // Validate confidence score
            Double confidenceScore = response.confidenceScore;
            if (confidenceScore < faceConfig.getConfidenceThreshold()) {
                log.warn("Face verification failed - confidence score {} below threshold {} for user: {}",
                        confidenceScore, faceConfig.getConfidenceThreshold(), user.getId());
                throw new FaceNotMatchException(
                        "Face does not match. Confidence score below threshold.",
                        confidenceScore,
                        faceConfig.getConfidenceThreshold()
                );
            }

            log.info("Face verified successfully for user: {} with confidence: {}", 
                    user.getId(), confidenceScore);
            return confidenceScore;

        } catch (FaceNotMatchException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Rest client error during face verification for user: {}", user.getId(), e);
            throw new FaceRecognitionException(
                    "Failed to connect to face recognition service",
                    "SERVICE_UNAVAILABLE",
                    e
            );
        } catch (Exception e) {
            log.error("Unexpected error during face verification for user: {}", user.getId(), e);
            throw new FaceRecognitionException(
                    "Unexpected error during face verification",
                    "VERIFICATION_ERROR",
                    e
            );
        }
    }

    /**
     * Register/Capture a new face for a user
     * 
     * Flow:
     * 1. Send face image to Python AI service
     * 2. Service generates face embedding
     * 3. Receive face embedding ID and confidence score
     * 4. Store embedding ID in user profile (NOT the raw embedding)
     * 5. Enable face recognition for user
     * 
     * @param user The user registering their face
     * @param faceImage Base64 encoded face image
     * @return Face embedding ID returned from AI service
     * @throws FaceRecognitionException on service error
     */
    public String registerFace(User user, String faceImage) {
        log.debug("Registering face for user: {}", user.getId());

        try {
            // Prepare registration request
            Map<String, Object> registerRequest = new HashMap<>();
            registerRequest.put("image", faceImage);  // Base64 encoded image
            registerRequest.put("user_id", user.getId().toString());
            registerRequest.put("description", "Face registration for " + user.getEmail());

            // Call Python AI service to generate embedding
            FaceRegistrationResponse response = callFaceRegistrationService(registerRequest);

            // Get face embedding ID from service
            String faceEmbeddingId = response.faceEmbeddingId;

            // Update user profile with embedding ID
            user.setFaceEmbeddingId(faceEmbeddingId);
            user.setFaceRecognitionEnabled(true);
            user.setFaceEnabledAt(java.time.LocalDateTime.now());
            User updatedUser = userRepository.save(user);

            log.info("Face registered successfully for user: {} with embedding ID: {}",
                    user.getId(), faceEmbeddingId);
            
            return faceEmbeddingId;

        } catch (RestClientException e) {
            log.error("Rest client error during face registration for user: {}", user.getId(), e);
            throw new FaceRecognitionException(
                    "Failed to connect to face recognition service",
                    "SERVICE_UNAVAILABLE",
                    e
            );
        } catch (Exception e) {
            log.error("Unexpected error during face registration for user: {}", user.getId(), e);
            throw new FaceRecognitionException(
                    "Unexpected error during face registration",
                    "REGISTRATION_ERROR",
                    e
            );
        }
    }

    /**
     * Update/Replace a user's face registration
     * 
     * @param user The user updating their face
     * @param newFaceImage Base64 encoded new face image
     * @return New face embedding ID
     */
    public String updateFaceRegistration(User user, String newFaceImage) {
        log.debug("Updating face registration for user: {}", user.getId());

        // For simplicity, we're reusing register logic
        // In production, might want to keep history of previous faces
        return registerFace(user, newFaceImage);
    }

    /**
     * Call Python AI service for face verification
     * 
     * @param verifyRequest Map containing image and embedding reference
     * @return Response from AI service
     * @throws FaceRecognitionException on error
     */
    private FaceVerificationResponse callFaceVerificationService(Map<String, Object> verifyRequest) {
        String endpoint = faceConfig.getFaceServiceUrl() + faceConfig.getVerifyEndpoint();
        log.debug("Calling face verification service: {}", endpoint);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(verifyRequest, headers);

            ResponseEntity<FaceVerificationResponse> response = faceRecognitionRestTemplate.postForEntity(
                    endpoint,
                    entity,
                    FaceVerificationResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new FaceRecognitionException(
                    "Invalid response from face verification service",
                    "INVALID_RESPONSE"
            );

        } catch (RestClientException e) {
            log.error("Error calling face verification service: {}", endpoint, e);
            throw new FaceRecognitionException(
                    "Failed to call face verification service: " + e.getMessage(),
                    "SERVICE_ERROR",
                    e
            );
        }
    }

    /**
     * Call Python AI service for face registration
     * 
     * @param registerRequest Map containing image and user info
     * @return Response from AI service
     * @throws FaceRecognitionException on error
     */
    private FaceRegistrationResponse callFaceRegistrationService(Map<String, Object> registerRequest) {
        String endpoint = faceConfig.getFaceServiceUrl() + faceConfig.getRegisterEndpoint();
        log.debug("Calling face registration service: {}", endpoint);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(registerRequest, headers);

            ResponseEntity<FaceRegistrationResponse> response = faceRecognitionRestTemplate.postForEntity(
                    endpoint,
                    entity,
                    FaceRegistrationResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new FaceRecognitionException(
                    "Invalid response from face registration service",
                    "INVALID_RESPONSE"
            );

        } catch (RestClientException e) {
            log.error("Error calling face registration service: {}", endpoint, e);
            throw new FaceRecognitionException(
                    "Failed to call face registration service: " + e.getMessage(),
                    "SERVICE_ERROR",
                    e
            );
        }
    }

    /**
     * Inner class representing face verification response from AI service
     */
    public static class FaceVerificationResponse {
        public boolean matches;
        public Double confidenceScore;
        public String message;
        public Map<String, Object> details;
    }

    /**
     * Inner class representing face registration response from AI service
     */
    public static class FaceRegistrationResponse {
        public String faceEmbeddingId;
        public Double confidenceScore;
        public String status;
        public Map<String, Object> details;
    }
}
