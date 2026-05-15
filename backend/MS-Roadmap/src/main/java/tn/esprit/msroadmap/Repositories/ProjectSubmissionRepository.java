package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.ProjectSubmission;
import tn.esprit.msroadmap.Enums.SubmissionStatus;

import java.util.List;

@Repository
public interface ProjectSubmissionRepository extends JpaRepository<ProjectSubmission, Long> {
    List<ProjectSubmission> findByUserId(Long userId);
    ProjectSubmission findByUserIdAndProjectSuggestionId(Long userId, Long suggestionId);
    List<ProjectSubmission> findByUserIdAndStatus(Long userId, SubmissionStatus status);
    boolean existsByUserIdAndProjectSuggestionIdAndStatus(Long userId, Long suggestionId, SubmissionStatus status);
}
