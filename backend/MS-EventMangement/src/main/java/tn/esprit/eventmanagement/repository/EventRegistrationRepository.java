package tn.esprit.eventmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventRegistration;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {



    List<EventRegistration> findByEventId(Long eventId);

    List<EventRegistration> findByUserId(Long userId);
    boolean existsByEventIdAndUserId(Long eventId, Long userId);
    Optional<EventRegistration> findByUserIdAndEventId(Long userId, Long eventId);
    Optional<EventRegistration> findByCertificateCode(String code);

}