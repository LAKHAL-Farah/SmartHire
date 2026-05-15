package tn.esprit.eventmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.eventmanagement.entities.EventTag;

import java.util.List;

@Repository
public interface EventTagRepository extends JpaRepository<EventTag,Long> {



    @Query("SELECT t FROM EventTag t JOIN t.events e WHERE e.id = :eventId")
    List<EventTag> findTagsByEventId(@Param("eventId") Long eventId);
}
