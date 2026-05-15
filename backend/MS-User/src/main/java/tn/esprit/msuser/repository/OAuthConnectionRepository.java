package tn.esprit.msuser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msuser.entity.OAuthConnection;
import tn.esprit.msuser.entity.enumerated.AuthProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthConnectionRepository extends JpaRepository<OAuthConnection, UUID> {

    List<OAuthConnection> findByUserId(UUID userId);

    Optional<OAuthConnection> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    @Query("SELECT o FROM OAuthConnection o WHERE o.user.id = :userId AND o.provider = :provider")
    Optional<OAuthConnection> findByUserIdAndProvider(@Param("userId") UUID userId, @Param("provider") AuthProvider provider);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}