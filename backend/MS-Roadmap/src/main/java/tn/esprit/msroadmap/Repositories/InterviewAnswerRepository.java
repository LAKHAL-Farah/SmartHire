package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.InterviewAnswer;

import java.util.List;

@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {
    int countBySessionId(Long sessionId);
    List<InterviewAnswer> findBySessionIdOrderBySubmittedAtAsc(Long sessionId);
}
