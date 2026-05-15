package tn.esprit.msuser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msuser.entity.FaceVerificationToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FaceVerificationToken entity
 * Handles creation, retrieval, and management of temporary face verification tokens
 */
@Repository
public interface FaceVerificationTokenRepository extends JpaRepository<FaceVerificationToken, UUID> {

    /**
     * Find a valid (not used, not expired, not revoked) token by token code
     */
    @Query("SELECT t FROM FaceVerificationToken t " +
           "WHERE t.tokenCode = :tokenCode " +
           "AND t.used = false " +
           "AND t.revoked = false " +
           "AND t.expirationTime > CURRENT_TIMESTAMP " +
           "AND t.attemptCount < t.maxAttempts")
    Optional<FaceVerificationToken> findValidTokenByCode(@Param("tokenCode") String tokenCode);

    /**
     * Find the most recent token for a user (regardless of status)
     * Useful for cleanup and auditing
     */
    @Query("SELECT t FROM FaceVerificationToken t " +
           "WHERE t.user.id = :userId " +
           "ORDER BY t.createdAt DESC LIMIT 1")
    Optional<FaceVerificationToken> findMostRecentTokenByUserId(@Param("userId") UUID userId);

    /**
     * Find active tokens for a user (not used, not revoked, not expired)
     * Should typically return 0 or 1
     */
    @Query("SELECT t FROM FaceVerificationToken t " +
           "WHERE t.user.id = :userId " +
           "AND t.used = false " +
           "AND t.revoked = false " +
           "AND t.expirationTime > CURRENT_TIMESTAMP")
    Optional<FaceVerificationToken> findActiveTokenByUserId(@Param("userId") UUID userId);

    /**
     * Delete all expired tokens (cleanup task)
     * Should be run periodically by a scheduler
     */
    @Query("DELETE FROM FaceVerificationToken t WHERE t.expirationTime < :cutoffTime")
    void deleteExpiredTokensBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count active tokens for a user
     * Used to prevent token exhaustion attacks
     */
    @Query("SELECT COUNT(t) FROM FaceVerificationToken t " +
           "WHERE t.user.id = :userId " +
           "AND t.used = false " +
           "AND t.revoked = false " +
           "AND t.expirationTime > CURRENT_TIMESTAMP")
    long countActiveTokensByUserId(@Param("userId") UUID userId);
}
