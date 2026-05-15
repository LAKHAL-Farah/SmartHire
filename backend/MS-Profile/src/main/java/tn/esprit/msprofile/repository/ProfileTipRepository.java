package tn.esprit.msprofile.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msprofile.entity.ProfileTip;
import tn.esprit.msprofile.entity.enums.ProfileType;

import java.util.List;
import java.util.UUID;

public interface ProfileTipRepository extends JpaRepository<ProfileTip, UUID> {
    List<ProfileTip> findByUserId(UUID userId);
    List<ProfileTip> findByUserIdAndIsResolvedFalse(UUID userId);
    List<ProfileTip> findByUserIdAndProfileType(UUID userId, ProfileType type);
    List<ProfileTip> findByUserIdAndProfileTypeAndIsResolvedFalse(UUID userId, ProfileType type);
    List<ProfileTip> findByUserIdOrderByPriorityAscCreatedAtDesc(UUID userId);
    void deleteBySourceEntityId(UUID sourceEntityId);
    int countByUserIdAndIsResolvedFalse(UUID userId);
}

