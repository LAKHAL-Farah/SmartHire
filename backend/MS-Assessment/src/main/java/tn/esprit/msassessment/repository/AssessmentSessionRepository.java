package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msassessment.entity.AssessmentSession;
import tn.esprit.msassessment.entity.enums.SessionStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, Long> {

    @EntityGraph(attributePaths = {"category"})
    @Query("select s from AssessmentSession s where s.id = :id")
    Optional<AssessmentSession> findWithCategoryById(@Param("id") Long id);

    /**
     * Fetch sessions for a user with their categories.
     * Uses @EntityGraph instead of JOIN FETCH to avoid Hibernate 7 UUID parameter binding bug
     * where JOIN FETCH + UUID params generates SQL returning zero rows despite records existing.
     */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM AssessmentSession s WHERE s.userId = :userId ORDER BY s.startedAt DESC")
    List<AssessmentSession> findByUserIdOrderByStartedAtDesc(@Param("userId") String userId);

    @EntityGraph(attributePaths = {"category"})
    List<AssessmentSession> findByStatusAndResultReleasedToCandidateIsFalseOrderByCompletedAtDesc(SessionStatus status);

    @EntityGraph(attributePaths = {"category"})
    List<AssessmentSession> findByStatusOrderByCompletedAtDesc(SessionStatus status);

    boolean existsByUserIdAndCategory_IdAndStatus(String userId, Long categoryId, SessionStatus status);

    /** Any session row for this user and category (one attempt per category). */
    boolean existsByUserIdAndCategory_Id(String userId, Long categoryId);

    /** Completed sessions whose results are visible to the candidate — used to build {@link tn.esprit.msassessment.entity.SkillProfile}. */
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT s FROM AssessmentSession s WHERE s.userId = :userId AND s.status = :status AND s.resultReleasedToCandidate = true ORDER BY s.completedAt DESC")
    List<AssessmentSession> findPublishedCompletedForUser(
            @Param("userId") String userId, @Param("status") SessionStatus status);
}
