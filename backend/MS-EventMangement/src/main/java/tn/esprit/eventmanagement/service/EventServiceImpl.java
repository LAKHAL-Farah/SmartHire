package tn.esprit.eventmanagement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.eventmanagement.DTO.event.EventDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventRegistration;
import tn.esprit.eventmanagement.entities.RegistrationStatus;
import tn.esprit.eventmanagement.repository.EventRegistrationRepository;
import tn.esprit.eventmanagement.repository.EventRepository;

import tn.esprit.eventmanagement.service.EventService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service

@Transactional
public class EventServiceImpl implements EventService {
    @Autowired
    private final EventRepository eventRepository;
    @Autowired
    private final EventRegistrationRepository registrationRepository;
    @Autowired
    private final AiSummaryService aiSummaryService;




    @Autowired
    public EventServiceImpl (EventRepository eventRepository, EventRegistrationRepository registrationRepository, AiSummaryService aiSummaryService) {
        this.eventRepository = eventRepository;
        this.registrationRepository =registrationRepository;
        this.aiSummaryService = aiSummaryService;
    }
    @Override
    public EventDTO createEvent(EventDTO eventDTO) {
        Event event = mapToEntity(eventDTO);
        Event savedEvent = eventRepository.save(event);
        return mapToDTO(savedEvent);
    }

    @Override
    public EventDTO updateEvent(Long id, EventDTO eventDTO) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id " + id));

        // Mise à jour des champs
        event.setTitle(eventDTO.getTitle());
        event.setDescription(eventDTO.getDescription());
        event.setType(eventDTO.getType());
        event.setStatus(eventDTO.getStatus());
        event.setLocation(eventDTO.getLocation());
        event.setOnline(eventDTO.getOnline());
        event.setOnlineUrl(eventDTO.getOnlineUrl());
        event.setDomain(eventDTO.getDomain());
        event.setStartDate(eventDTO.getStartDate());
        event.setEndDate(eventDTO.getEndDate());
        event.setMaxCapacity(eventDTO.getMaxCapacity());
        event.setCurrentRegistrations(eventDTO.getCurrentRegistrations());
        event.setOrganizerId(eventDTO.getOrganizerId());
        event.setAiSummary(eventDTO.getAiSummary());
        event.setTags(eventDTO.getTags());

        Event updatedEvent = eventRepository.save(event);
        return mapToDTO(updatedEvent);
    }

    @Override
    public void deleteEvent(Long id) {

        this.eventRepository.deleteById(id);
    }

    @Override
    public EventDTO getEventById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with id " + id));
        return mapToDTO(event);
    }

    @Override
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Mapping DTO ↔ Entity
    private EventDTO mapToDTO(Event event) {
        EventDTO dto = new EventDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setType(event.getType());
        dto.setStatus(event.getStatus());
        dto.setLocation(event.getLocation());
        dto.setOnline(event.getOnline());
        dto.setOnlineUrl(event.getOnlineUrl());
        dto.setDomain(event.getDomain());
        dto.setStartDate(event.getStartDate());
        dto.setEndDate(event.getEndDate());
        dto.setMaxCapacity(event.getMaxCapacity());
        dto.setCurrentRegistrations(event.getCurrentRegistrations());
        dto.setOrganizerId(event.getOrganizerId());
        dto.setAiSummary(event.getAiSummary());
        dto.setTags(event.getTags());
        return dto;
    }

    private Event mapToEntity(EventDTO dto) {
        Event event = new Event();
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setType(dto.getType());
        event.setStatus(dto.getStatus());
        event.setLocation(dto.getLocation());
        event.setOnline(dto.getOnline());
        event.setOnlineUrl(dto.getOnlineUrl());
        event.setDomain(dto.getDomain());
        event.setStartDate(dto.getStartDate());
        event.setEndDate(dto.getEndDate());
        event.setMaxCapacity(dto.getMaxCapacity());
        event.setCurrentRegistrations(dto.getCurrentRegistrations());
        event.setOrganizerId(dto.getOrganizerId());
        event.setAiSummary(dto.getAiSummary());
        event.setTags(dto.getTags());
        return event;
    }

    @Transactional
    @Override
    public void registerToEvent(Long eventId, Long userId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // ❗ Vérifier capacité
        if (event.getCurrentRegistrations() >= event.getMaxCapacity()) {
            throw new RuntimeException("Event is full");
        }

        // ❗ éviter double inscription
        boolean exists = registrationRepository
                .existsByEventIdAndUserId(eventId, userId);
        System.out.println(userId);
        if (exists) {
            throw new RuntimeException("User already registered");
        }

        // 🔥 créer registration
        EventRegistration registration = new EventRegistration();
        registration.setUserId(userId);
        registration.setEvent(event);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setRegisteredAt(LocalDateTime.now());
        registration.setAttended(false);


        registrationRepository.save(registration);

        // 🔥 incrémenter
        event.setCurrentRegistrations(
                event.getCurrentRegistrations() + 1
        );

        eventRepository.save(event);
    }
    public Event generateAndSaveAiSummary(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        String summary = aiSummaryService.generateSummary(event);
        event.setAiSummary(summary);

        return eventRepository.save(event);
    }
}