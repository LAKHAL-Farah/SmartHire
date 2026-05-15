package tn.esprit.eventmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.eventmanagement.entities.EventSpeaker;
@Repository
public interface EventSpeakerRepository extends JpaRepository<EventSpeaker,Long> {
}
