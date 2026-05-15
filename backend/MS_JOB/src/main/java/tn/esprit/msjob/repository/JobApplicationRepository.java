package tn.esprit.msjob.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msjob.entity.JobApplication;

import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    boolean existsByJob_IdAndUserId(Long jobId, Long userId);

    List<JobApplication> findByJob_IdOrderByAppliedAtDesc(Long jobId);

    List<JobApplication> findByUserIdOrderByAppliedAtDesc(Long userId);
}

