package tn.esprit.msuser.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msuser.dto.request.*;
import tn.esprit.msuser.dto.response.*;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.exception.FaceNotMatchException;
import tn.esprit.msuser.exception.FaceRecognitionException;
import tn.esprit.msuser.exception.InvalidFaceTokenException;
import tn.esprit.msuser.repository.UserRepository;
import tn.esprit.msuser.service.*;

import java.util.Map;
import java.util.UUID;

/**
 * Authentication Controller
 * 
 * Endpoints:
 * - Traditional login: /auth/connexion
 * - Face Recognition MFA:
 *   - Step 1: /auth/login-mfa (username/password -> FACE_REQUIRED with temp token)
 *   - Step 2: /auth/verify-face (temp token + face image -> JWT)
 *   - Setup: /auth/enable-face-recognition (register face)
 *   - Disable: /auth/disable-face-recognition (disable MFA)
 * - Password reset: /auth/forgot-password, /auth/reset-password
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private FaceVerificationTokenService faceTokenService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Traditional login endpoint (existing)
     * Returns JWT immediately if no MFA is enabled
     */
    @PostMapping("/connexion")
    public AuthResponse connexion(@RequestBody Map<String, String> request) {
        log.debug("Traditional login attempt for user: {}", request.get("mail"));
        
        Authentication authenticate = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.get("mail"), request.get("password"))
        );

        if (authenticate.isAuthenticated()) {
            User user = (User) authenticate.getPrincipal();
            assert user != null;
            return jwtService.GenerateToken(user);
        } else {
            throw new RuntimeException("Accès refusé");
        }
    }

    /**
     * Step 1: Login with MFA Support
     * 
     * If face recognition is DISABLED:
     *   → Returns JWT immediately (same as /connexion)
     * 
     * If face recognition is ENABLED:
     *   → Returns: {
     *       "status": "FACE_REQUIRED",
     *       "tempToken": "...",
     *       "expiresIn": 45
     *     }
     *   → User must call /auth/verify-face with this temp token
     * 
     * Request body:
     * {
     *   "email": "user@example.com",
     *   "password": "password123"
     * }
     * 
     * @param request Login request with email and password
     * @return JWT if no MFA, or FACE_REQUIRED response if MFA enabled
     */
    @PostMapping("/login-mfa")
    public ResponseEntity<?> loginWithMfa(@Valid @RequestBody FaceLoginRequest request) {
        log.info("MFA login attempt for user: {}", request.getEmail());
        
        try {
            // Authenticate user with standard credentials
            Authentication authenticate = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            if (!authenticate.isAuthenticated()) {
                log.warn("Authentication failed for user: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(GenericApiResponse.unauthorized("Invalid email or password"));
            }

            User user = (User) authenticate.getPrincipal();
            assert user != null;

            // Check if face recognition is enabled for this user
            if (!user.getFaceRecognitionEnabled()) {
                log.debug("Face recognition disabled for user: {}. Returning full JWT.", user.getId());
                // No MFA required, return full JWT
                AuthResponse fullAuth = jwtService.GenerateToken(user);
                return ResponseEntity.ok(GenericApiResponse.success(
                        "Authentication successful",
                        fullAuth
                ));
            }

            // Face recognition enabled - return temp token instead
            log.debug("Face recognition enabled for user: {}. Requesting face verification.", user.getId());
            
            // Create temporary token for face verification
            String tempToken = faceTokenService.createFaceVerificationToken(user);
            
            // Return FACE_REQUIRED response with temporary token
            FaceLoginResponse faceRequired = FaceLoginResponse.createFaceRequired(
                    tempToken,
                    45,  // Token expires in 45 seconds
                    user.getId().toString(),
                    user.getEmail()
            );

            return ResponseEntity.ok(GenericApiResponse.faceRequired(
                    "Face recognition required for complete authentication",
                    faceRequired
            ));

        } catch (Exception e) {
            log.error("Error during MFA login for user: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(GenericApiResponse.unauthorized("Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Step 2: Verify Face and Complete MFA
     * 
     * After successful username/password auth with face recognition enabled:
     * 1. Client receives temporary token from /login-mfa
     * 2. Client captures user's face and sends to this endpoint
     * 3. Service verifies face against registered face embedding
     * 4. If match: returns full JWT
     * 5. If no match: returns UNAUTHORIZED
     * 
     * Request body:
     * {
     *   "tempToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "image": "base64encodedimagedata==",
     *   "imageFormat": "jpeg"
     * }
     * 
     * Success response: {
     *   "status": "SUCCESS",
     *   "token": "full_jwt_token",
     *   "userId": "user_uuid",
     *   "email": "user@example.com"
     * }
     * 
     * @param request Face verification request with temp token and image
     * @return Full JWT on success, UNAUTHORIZED on failure
     */
    @PostMapping("/verify-face")
    public ResponseEntity<?> verifyFace(@Valid @RequestBody FaceVerifyRequest request) {
        log.debug("Face verification attempt with temp token");
        
        try {
            // Validate and retrieve temp token
            var tempToken = faceTokenService.validateAndRetrieveToken(request.getTempToken());
            User user = tempToken.getUser();
            log.debug("Temp token validated for user: {}", user.getId());

            try {
                // Verify face against stored embedding
                Double confidenceScore = faceRecognitionService.verifyFace(user, request);
                log.info("Face verified for user: {} with confidence: {}", user.getId(), confidenceScore);

                // Mark temp token as used (prevents replay attacks)
                faceTokenService.markTokenAsUsed(request.getTempToken());

                // Generate full JWT token
                AuthResponse fullAuth = jwtService.GenerateToken(user);

                FaceVerifyResponse successResponse = FaceVerifyResponse.createSuccess(
                        fullAuth.Token(),
                        user.getId(),
                        user.getEmail(),
                        fullAuth.userName(),
                        fullAuth.roles(),
                        confidenceScore
                );

                return ResponseEntity.ok(GenericApiResponse.success(
                        "Face verified successfully",
                        successResponse
                ));

            } catch (FaceNotMatchException e) {
                // Face doesn't match - increment attempt counter and return unauthorized
                log.warn("Face verification failed for user: {} - Confidence: {}", 
                        user.getId(), e.getConfidenceScore());
                
                faceTokenService.incrementVerificationAttempt(tempToken);

                FaceVerifyResponse failureResponse = FaceVerifyResponse.createUnauthorized(
                        "Face does not match. Please try again.",
                        e.getConfidenceScore()
                );

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(GenericApiResponse.error("Face does not match. Please try again.", failureResponse));
            }

        } catch (InvalidFaceTokenException e) {
            log.warn("Invalid face token: {}", e.getReason());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(GenericApiResponse.unauthorized("Invalid or expired token: " + e.getReason()));
        } catch (FaceRecognitionException e) {
            log.error("Face recognition service error: {}", e.getErrorCode(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(GenericApiResponse.error("Face recognition service unavailable"));
        } catch (Exception e) {
            log.error("Unexpected error during face verification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GenericApiResponse.error("Unexpected error during face verification"));
        }
    }

    /**
     * Enable Face Recognition MFA
     * 
     * User sends their face image to register for MFA
     * 1. Face image is sent to Python AI service
     * 2. Face embedding is generated and stored securely
     * 3. Face recognition is enabled for future logins
     * 4. User ID must be obtained from authentication context
     * 
     * Request body:
     * {
     *   "image": "base64encodedimagedata==",
     *   "imageFormat": "jpeg"
     * }
     * 
     * Success response: {
     *   "status": "SUCCESS",
     *   "message": "Face recognition enabled successfully",
     *   "faceRecognitionEnabled": true,
     *   "faceEmbeddingId": "emb_xyz123"
     * }
     * 
     * @param userId User ID (from authentication context or path param)
     * @param request Enable face recognition request with face image
     * @return Response with success/error status
     */
    @PutMapping("/enable-face-recognition/{userId}")
    public ResponseEntity<?> enableFaceRecognition(
            @PathVariable UUID userId,
            @Valid @RequestBody EnableFaceRecognitionRequest request) {
        
        log.info("Enable face recognition request for user: {}", userId);
        
        try {
            // In production, validate that userId matches authenticated user
            // For now, accepting it from path parameter
            
            // Find user from security context
//            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
//            var userOpt = java.util.Optional.ofNullable(
//                    authentication != null ? authentication.getPrincipal() : null
//            );
//
//            if (userOpt.isEmpty() || !(userOpt.get() instanceof User)) {
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body(GenericApiResponse.unauthorized("User not authenticated"));
//            }

            User user = userRepository.findById(userId).orElseThrow();

            // Register face with AI service
            String faceEmbeddingId = faceRecognitionService.registerFace(user, request.getImage());
            log.info("Face registered for user: {} with embedding ID: {}", user.getId(), faceEmbeddingId);

            EnableFaceRecognitionResponse response = EnableFaceRecognitionResponse.createSuccess(
                    user.getId().toString(),
                    user.getEmail(),
                    faceEmbeddingId,
                    0.95  // Placeholder confidence score
            );

            return ResponseEntity.ok(GenericApiResponse.success(
                    "Face recognition enabled successfully",
                    response
            ));

        } catch (FaceRecognitionException e) {
            log.error("Face recognition service error: {}", e.getErrorCode(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(GenericApiResponse.error("Failed to register face: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error enabling face recognition for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GenericApiResponse.error("Failed to enable face recognition: " + e.getMessage()));
        }
    }

    /**
     * Disable Face Recognition MFA
     * 
     * User can disable face recognition to go back to password-only login
     * 
     * @param userId User ID
     * @return Response with success status
     */
    @PutMapping("/disable-face-recognition/{userId}")
    public ResponseEntity<?> disableFaceRecognition(@PathVariable UUID userId) {
        log.info("Disable face recognition request for user: {}", userId);
        
        try {
            User user = userRepository.findById(userId).orElseThrow();

            user.setFaceRecognitionEnabled(false);
            user.setFaceEmbeddingId(null);


            log.info("Face recognition disabled for user: {}", user.getId());
            userRepository.save(user);
            return ResponseEntity.ok(GenericApiResponse.success(
                    "Face recognition disabled successfully",
                    null
            ));

        } catch (Exception e) {
            log.error("Error disabling face recognition for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GenericApiResponse.error("Failed to disable face recognition"));
        }
    }

    /**
     * Forgot password endpoint (existing)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        PasswordResetResponse response = passwordResetService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset password endpoint (existing)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<PasswordResetResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetResponse response = passwordResetService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}