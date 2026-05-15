package tn.esprit.eventmanagement.service;

import tn.esprit.eventmanagement.DTO.event.EventDTO;
import tn.esprit.eventmanagement.entities.Event;

import java.util.List;

public interface EventService {

    EventDTO createEvent(EventDTO eventDTO);

    EventDTO updateEvent(Long id, EventDTO eventDTO);

    void deleteEvent(Long id);

    EventDTO getEventById(Long id);

    List<EventDTO> getAllEvents();
    void registerToEvent(Long eventId, Long userId);
}