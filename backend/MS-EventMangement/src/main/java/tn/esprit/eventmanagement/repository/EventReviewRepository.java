package tn.esprit.eventmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.eventmanagement.entities.EventReview;

import java.util.List;

@Repository
public interface EventReviewRepository extends JpaRepository<EventReview,Long> {

    List<EventReview> findByEventId(Long eventId);


    boolean existsByUserIdAndEventId(Long userId, Long eventId);
}
