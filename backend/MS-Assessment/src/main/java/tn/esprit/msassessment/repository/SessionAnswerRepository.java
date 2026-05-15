package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msassessment.entity.SessionAnswer;

import java.util.List;

@Repository
public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, Long> {

    /** Loads questions with all choices for review screens. */
    @Query(
            "select distinct sa from SessionAnswer sa join fetch sa.question q join fetch q.choices join fetch sa.selectedChoice "
                    + "where sa.session.id = :sid")
    List<SessionAnswer> findDetailBySession(@Param("sid") Long sessionId);

    boolean existsByQuestion_Id(Long questionId);

    boolean existsBySelectedChoice_Id(Long choiceId);
}
