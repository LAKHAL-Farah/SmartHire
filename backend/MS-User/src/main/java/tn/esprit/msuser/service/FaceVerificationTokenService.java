package tn.esprit.msuser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.entity.FaceVerificationToken;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.exception.InvalidFaceTokenException;
import tn.esprit.msuser.repository.FaceVerificationTokenRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing Face Verification Temporary Tokens
 * 
 * Responsible for:
 * - Creating temporary tokens after successful username/password auth
 * - Validating tokens before face verification
 * - Marking tokens as used (preventing replay attacks)
 * - Revoking tokens on suspicious activity
 * - Cleanup of expired tokens
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FaceVerificationTokenService {

    private final FaceVerificationTokenRepository tokenRepository;

    @Value("${face.verification.token.expires.seconds:45}")
    private int tokenExpirationSeconds;

    @Value("${face.verification.token.max.attempts:3}")
    private int maxVerificationAttempts;

    @Value("${face.verification.prevent.duplicate.tokens:false}")
    private boolean preventDuplicateTokens;

    /**
     * Create a new face verification temporary token for a user
     * 
     * This token is returned after successful username/password authentication
     * when face recognition MFA is enabled.
     * 
     * @param user The authenticated user
     * @return Generated temporary token
     * @throws InvalidFaceTokenException if token creation fails
     */
    public String createFaceVerificationToken(User user) {
        log.debug("Creating face verification token for user: {}", user.getId());

        try {
            // Optionally prevent multiple active tokens for same user (prevents token exhaustion attacks)
            if (preventDuplicateTokens) {
                var existingToken = tokenRepository.findActiveTokenByUserId(user.getId());
                if (existingToken.isPresent()) {
                    log.warn("Active token already exists for user: {}. Revoking old token.", user.getId());
                    revokeToken(existingToken.get().getTokenCode(), "New token requested");
                }
            }

            // Generate unique token code (using UUID for simplicity)
            // In production, consider using a cryptographically secure random token
            String tokenCode = generateSecureTokenCode();

            // Calculate expiration time
            LocalDateTime expirationTime = LocalDateTime.now().plusSeconds(tokenExpirationSeconds);

            // Create and save token
            FaceVerificationToken token = FaceVerificationToken.builder()
                    .tokenCode(tokenCode)
                    .user(user)
                    .expirationTime(expirationTime)
                    .used(false)
                    .revoked(false)
                    .attemptCount(0)
                    .maxAttempts(maxVerificationAttempts)
                    .build();

            FaceVerificationToken savedToken = tokenRepository.save(token);
            log.info("Face verification token created for user: {} with expiration in {} seconds", 
                    user.getId(), tokenExpirationSeconds);

            return savedToken.getTokenCode();

        } catch (Exception e) {
            log.error("Error creating face verification token for user: {}", user.getId(), e);
            throw new InvalidFaceTokenException("Failed to create face verification token", "TOKEN_CREATION_ERROR");
        }
    }

    /**
     * Validate and retrieve a face verification token
     * 
     * Checks token validity including:
     * - Existence
     * - Not expired
     * - Not already used
     * - Not revoked
     * - Attempt count not exceeded
     * 
     * @param tokenCode The token code to validate
     * @return Valid FaceVerificationToken
     * @throws InvalidFaceTokenException if token is invalid
     */
    public FaceVerificationToken validateAndRetrieveToken(String tokenCode) {
        log.debug("Validating face verification token: {}", tokenCode);

        var tokenOpt = tokenRepository.findValidTokenByCode(tokenCode);
        
        if (tokenOpt.isEmpty()) {
            log.warn("Invalid or expired face verification token attempted: {}", tokenCode);
            throw new InvalidFaceTokenException(
                    "Face verification token is invalid, expired, or already used",
                    tokenCode,
                    "INVALID_OR_EXPIRED"
            );
        }

        FaceVerificationToken token = tokenOpt.get();

        // Additional validation checks
        if (token.isExpired()) {
            log.warn("Expired face verification token used: {} by user: {}", tokenCode, token.getUser().getId());
            throw new InvalidFaceTokenException(
                    "Face verification token has expired",
                    tokenCode,
                    "EXPIRED"
            );
        }

        if (token.getUsed()) {
            log.warn("Already used face verification token attempted: {} by user: {}", 
                    tokenCode, token.getUser().getId());
            throw new InvalidFaceTokenException(
                    "Face verification token has already been used",
                    tokenCode,
                    "ALREADY_USED_REPLAY_ATTACK"
            );
        }

        if (token.getRevoked()) {
            log.warn("Revoked face verification token attempted: {} by user: {}", 
                    tokenCode, token.getUser().getId());
            throw new InvalidFaceTokenException(
                    "Face verification token has been revoked",
                    tokenCode,
                    "REVOKED"
            );
        }

        if (token.getAttemptCount() >= token.getMaxAttempts()) {
            log.warn("Max attempts exceeded for face verification token: {} by user: {}", 
                    tokenCode, token.getUser().getId());
            revokeToken(tokenCode, "Max verification attempts exceeded");
            throw new InvalidFaceTokenException(
                    "Maximum verification attempts exceeded. Please request a new token.",
                    tokenCode,
                    "MAX_ATTEMPTS_EXCEEDED"
            );
        }

        return token;
    }

    /**
     * Mark token as used after successful verification
     * Prevents replay attacks by ensuring token can only be used once
     * 
     * @param tokenCode The token to mark as used
     */
    public void markTokenAsUsed(String tokenCode) {
        log.debug("Marking face verification token as used: {}", tokenCode);

        var tokenOpt = tokenRepository.findById(UUID.fromString(
                tokenRepository.findValidTokenByCode(tokenCode)
                        .orElseThrow(() -> new InvalidFaceTokenException("Token not found", tokenCode, "NOT_FOUND"))
                        .getId().toString()
        ));

        if (tokenOpt.isPresent()) {
            FaceVerificationToken token = tokenOpt.get();
            token.markAsUsed();
            tokenRepository.save(token);
            log.info("Face verification token marked as used for user: {}", token.getUser().getId());
        }
    }

    /**
     * Increment verification attempt counter
     * Used to track failed attempts and prevent brute force attacks
     * 
     * @param token The token to increment attempts for
     */
    public void incrementVerificationAttempt(FaceVerificationToken token) {
        log.debug("Incrementing verification attempt for token of user: {}", token.getUser().getId());
        
        token.incrementAttempt();
        tokenRepository.save(token);

        if (token.getAttemptCount() >= token.getMaxAttempts()) {
            log.warn("Max verification attempts reached for user: {}. Revoking token.", token.getUser().getId());
            revokeToken(token.getTokenCode(), "Max verification attempts exceeded");
        }
    }

    /**
     * Revoke a token before it expires
     * Used for security reasons (suspicious activity, explicit user action, etc.)
     * 
     * @param tokenCode Token to revoke
     * @param reason Reason for revocation (for audit trail)
     */
    public void revokeToken(String tokenCode, String reason) {
        log.debug("Revoking face verification token: {} - Reason: {}", tokenCode, reason);

        var tokenOpt = tokenRepository.findById(
                UUID.fromString(tokenRepository.findValidTokenByCode(tokenCode)
                        .orElseThrow(() -> new InvalidFaceTokenException("Token not found", tokenCode, "NOT_FOUND"))
                        .getId().toString())
        );

        if (tokenOpt.isPresent()) {
            FaceVerificationToken token = tokenOpt.get();
            token.setRevoked(true);
            token.setRevocationReason(reason);
            tokenRepository.save(token);
            log.info("Face verification token revoked for user: {}", token.getUser().getId());
        }
    }

    /**
     * Get the most recent token for a user (for debugging/audit)
     * 
     * @param userId User ID
     * @return Most recent token or empty
     */
    public java.util.Optional<FaceVerificationToken> getMostRecentTokenForUser(UUID userId) {
        return tokenRepository.findMostRecentTokenByUserId(userId);
    }

    /**
     * Cleanup expired tokens (should be run periodically by a scheduler)
     * Removes old tokens to keep database clean
     * 
     * @return Number of tokens deleted
     */
    @Transactional
    public int cleanupExpiredTokens() {
        log.debug("Cleaning up expired face verification tokens");
        
        LocalDateTime cutoffTime = LocalDateTime.now();
        
        try {
            // Query to count and delete
            var expiredTokens = tokenRepository.findAll().stream()
                    .filter(token -> token.isExpired() && !token.getUsed())
                    .toList();
            
            int count = expiredTokens.size();
            tokenRepository.deleteAll(expiredTokens);
            
            log.info("Cleaned up {} expired face verification tokens", count);
            return count;
        } catch (Exception e) {
            log.error("Error during cleanup of expired tokens", e);
            return 0;
        }
    }

    /**
     * Generate a secure token code
     * In production, consider using a cryptographically secure random generator
     * 
     * @return Unique token code
     */
    private String generateSecureTokenCode() {
        // Using UUID as a simple approach
        // For enhanced security, consider using SecureRandom with proper encoding
        return UUID.randomUUID().toString();
    }

    /**
     * Extract user from a valid token
     * 
     * @param tokenCode The token code
     * @return User associated with the token
     */
    public User extractUserFromToken(String tokenCode) {
        var token = validateAndRetrieveToken(tokenCode);
        return token.getUser();
    }
}
