package tn.esprit.msuser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.entity.PasswordResetToken;
import tn.esprit.msuser.entity.User;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Trouve un token non utilisé par son code
     */
    Optional<PasswordResetToken> findByTokenCodeAndUsedFalse(String tokenCode);

    /**
     * Trouve les tokens non expirés d'un utilisateur
     */
    @Query("SELECT p FROM PasswordResetToken p WHERE p.user.id = :userId AND p.used = false AND p.expirationTime > :now")
    Optional<PasswordResetToken> findValidTokenByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Supprime les tokens expirés
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken p WHERE p.expirationTime < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
