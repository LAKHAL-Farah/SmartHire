package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.msassessment.entity.UserAssessmentAssignment;
import tn.esprit.msassessment.entity.enums.AssignmentStatus;

import java.util.List;
import java.util.Optional;

public interface UserAssessmentAssignmentRepository extends JpaRepository<UserAssessmentAssignment, Long> {

    Optional<UserAssessmentAssignment> findByUserId(String userId);

    List<UserAssessmentAssignment> findByStatusOrderByCreatedAtAsc(AssignmentStatus status);

    List<UserAssessmentAssignment> findByStatusOrderByApprovedAtDesc(AssignmentStatus status);

    List<UserAssessmentAssignment> findByStatusAndDismissedFromAdminOrderByApprovedAtDesc(
            AssignmentStatus status, boolean dismissedFromAdmin);
}
