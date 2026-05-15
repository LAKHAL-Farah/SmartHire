package tn.esprit.eventmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.eventmanagement.entities.HackathonSubmission;

import java.util.List;

@Repository
public interface HackathonSubmissionRepository extends JpaRepository<HackathonSubmission,Long> {
    List<HackathonSubmission> findByEventId(Long eventId);

    List<HackathonSubmission> findByUserId(Long userId);
}
