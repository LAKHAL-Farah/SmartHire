package tn.esprit.eventmanagement.service;

import tn.esprit.eventmanagement.DTO.registration.EventRegistrationDTO;
import tn.esprit.eventmanagement.entities.EventRegistration;

import java.util.List;

public interface EventRegistrationService {

    EventRegistrationDTO register(EventRegistrationDTO dto);

    List<EventRegistrationDTO> getAllRegistrations();

    EventRegistrationDTO getRegistrationById(Long id);

    List<EventRegistrationDTO> getRegistrationsByEvent(Long eventId);

    List<EventRegistrationDTO> getRegistrationsByUser(Long userId);

    EventRegistrationDTO updateRegistration(Long id, EventRegistrationDTO dto);

    void deleteRegistration(Long id);
     void registerToEvent(Long eventId, EventRegistration registration);

}