package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msassessment.entity.SkillProfile;

import java.util.Optional;

@Repository
public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {

    Optional<SkillProfile> findByUserId(String userId);

    void deleteByUserId(String userId);
}
