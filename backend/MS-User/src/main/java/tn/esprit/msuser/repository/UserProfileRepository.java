package tn.esprit.msuser.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msuser.entity.UserProfile;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Query("SELECT p FROM UserProfile p WHERE p.user.id = :userId")
    Optional<UserProfile> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM UserProfile p WHERE p.user.id = :userId")
    boolean existsByUserId(@Param("userId") UUID userId);
}