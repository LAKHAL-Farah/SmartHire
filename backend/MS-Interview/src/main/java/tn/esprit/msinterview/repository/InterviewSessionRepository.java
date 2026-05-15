package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findByUserIdOrderByStartedAtDesc(Long userId);

    List<InterviewSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    List<InterviewSession> findByUserIdAndRoleType(Long userId, RoleType roleType);

    List<InterviewSession> findByUserIdAndCareerPathId(Long userId, Long careerPathId);

    long countByUserIdAndStatus(Long userId, SessionStatus status);

    Optional<InterviewSession> findTopByUserIdOrderByStartedAtDesc(Long userId);

    Optional<InterviewSession> findTopByUserIdAndStatusInOrderByStartedAtDesc(Long userId, List<SessionStatus> statuses);

    default Optional<InterviewSession> findActiveSession(Long userId) {
        return findTopByUserIdAndStatusInOrderByStartedAtDesc(
                userId,
                List.of(SessionStatus.IN_PROGRESS, SessionStatus.PAUSED)
        );
    }

    List<InterviewSession> findByCareerPathIdAndStatus(Long careerPathId, SessionStatus status);

        @Query(value = "SELECT * FROM interview_sessions WHERE user_id = :userId ORDER BY started_at DESC", nativeQuery = true)
        List<InterviewSession> findAllByUserIdFromSessionTable(@Param("userId") Long userId);

        @Query(value = """
            SELECT *
            FROM interview_sessions
            WHERE user_id = :userId
              AND UPPER(TRIM(status)) = UPPER(:status)
            ORDER BY started_at DESC
            """, nativeQuery = true)
        List<InterviewSession> findAllByUserIdAndStatusFromSessionTable(
            @Param("userId") Long userId,
            @Param("status") String status
        );
}

