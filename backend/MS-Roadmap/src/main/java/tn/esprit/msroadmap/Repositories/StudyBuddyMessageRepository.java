package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.StudyBuddyMessage;

import java.util.List;

@Repository
public interface StudyBuddyMessageRepository extends JpaRepository<StudyBuddyMessage, Long> {
    List<StudyBuddyMessage> findByUserIdAndStepIdOrderByCreatedAtAsc(Long userId, Long stepId);
    void deleteByUserIdAndStepId(Long userId, Long stepId);
}
