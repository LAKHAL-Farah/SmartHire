package tn.esprit.eventmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.eventmanagement.DTO.event.EventDTO;
import tn.esprit.eventmanagement.entities.Event;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
   List<Event> findById(EventDTO dto );

   List<Event> findByStartDateBetween(LocalDateTime start, LocalDateTime end);
   @Query("SELECT e FROM Event e WHERE e.startDate > CURRENT_TIMESTAMP")
   List<Event> findUpcomingEvents();
}