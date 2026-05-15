package tn.esprit.msuser.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Temporary token for face verification step in MFA flow
 * 
 * Lifecycle:
 * 1. Created after successful username/password authentication
 * 2. Valid for 30-60 seconds only
 * 3. Can only be used once with /auth/verify-face endpoint
 * 4. Automatically expired after use or timeout
 * 5. Stored securely (in-memory cache for performance, optional DB backup)
 * 
 * Security considerations:
 * - Short lived (30-60 seconds)
 * - Single use only
 * - Links to specific user
 * - Can be revoked
 */
@Entity
@Table(name = "face_verification_tokens", indexes = {
        @Index(name = "idx_face_token_code", columnList = "token_code", unique = true),
        @Index(name = "idx_face_token_user_id", columnList = "user_id"),
        @Index(name = "idx_face_token_expiration", columnList = "expiration_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * UUID token code (can be hashed in production for additional security)
     */
    @Column(nullable = false, unique = true, length = 500)
    private String tokenCode;

    /**
     * User associated with this verification attempt
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Token expiration time (typically 30-60 seconds from creation)
     */
    @Column(nullable = false)
    private LocalDateTime expirationTime;

    /**
     * Whether this token has been used
     * Once used, it cannot be reused (prevents replay attacks)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    /**
     * Timestamp when token was used (for audit trail)
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * Whether this token was revoked before expiration
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    /**
     * Reason for revocation (if any)
     */
    @Column(length = 255)
    private String revocationReason;

    /**
     * Number of verification attempts with this token
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /**
     * Maximum allowed attempts before token is invalidated
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    /**
     * Creation timestamp
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Auto-set timestamps
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if token is still valid for verification
     * Valid if: not expired, not used, not revoked, not max attempts exceeded
     */
    public boolean isValid() {
        return !isExpired() && !used && !revoked && attemptCount < maxAttempts;
    }

    /**
     * Check if token has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expirationTime);
    }

    /**
     * Mark token as used
     */
    public void markAsUsed() {
        this.used = true;
        this.usedAt = LocalDateTime.now();
    }

    /**
     * Increment attempt count
     */
    public void incrementAttempt() {
        this.attemptCount++;
    }
}
