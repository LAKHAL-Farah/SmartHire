package tn.esprit.eventmanagement.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.DTO.registration.EventRegistrationDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventRegistration;
import tn.esprit.eventmanagement.entities.RegistrationStatus;
import tn.esprit.eventmanagement.repository.EventRegistrationRepository;
import tn.esprit.eventmanagement.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventRegistrationServiceImpl implements EventRegistrationService {

    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;


    @Autowired
    public EventRegistrationServiceImpl(EventRegistrationRepository registrationRepository,
                                        EventRepository eventRepository) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
    }

    // 🔄 Entity → DTO
    private EventRegistrationDTO mapToDTO(EventRegistration reg) {
        EventRegistrationDTO dto = new EventRegistrationDTO();
        dto.setId(reg.getId());
        dto.setUserId(reg.getUserId());
        dto.setStatus(reg.getStatus());
        dto.setRelevanceScore(reg.getRelevanceScore());
        dto.setRegisteredAt(reg.getRegisteredAt());
        dto.setConfirmedAt(reg.getConfirmedAt());
        dto.setAttended(reg.getAttended());
        dto.setCertificateUrl(reg.getCertificateUrl());
        dto.setEventId(reg.getEvent().getId());
        return dto;
    }

    // 🔄 DTO → Entity
    private EventRegistration mapToEntity(EventRegistrationDTO dto) {

        EventRegistration reg = new EventRegistration();
        reg.setId(dto.getId());
        reg.setUserId(dto.getUserId());
        reg.setStatus(dto.getStatus());
        reg.setRelevanceScore(dto.getRelevanceScore());
        reg.setRegisteredAt(dto.getRegisteredAt());
        reg.setConfirmedAt(dto.getConfirmedAt());
        reg.setAttended(dto.getAttended());
        reg.setCertificateUrl(dto.getCertificateUrl());

        Event event = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        reg.setEvent(event);

        return reg;
    }

    @Override
    public EventRegistrationDTO register(EventRegistrationDTO dto) {

        Event event = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // ✅ Vérification si l'event est full
        if (event.isFull()) {
            throw new RuntimeException("Event is FULL (Saturé)");
        }

        EventRegistration reg = mapToEntity(dto);
        reg.setRegisteredAt(LocalDateTime.now());
        reg.setStatus(RegistrationStatus.PENDING);

        // ✅ Incrémenter le compteur
        event.setCurrentRegistrations(event.getCurrentRegistrations() + 1);

        eventRepository.save(event);

        return mapToDTO(registrationRepository.save(reg));
    }
    @Override
    public List<EventRegistrationDTO> getAllRegistrations() {
        return registrationRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventRegistrationDTO getRegistrationById(Long id) {
        return registrationRepository.findById(id)
                .map(this::mapToDTO)
                .orElse(null);
    }

    @Override
    public List<EventRegistrationDTO> getRegistrationsByEvent(Long eventId) {
        return registrationRepository.findByEventId(eventId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventRegistrationDTO> getRegistrationsByUser(Long userId) {
        return registrationRepository.findByUserId(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventRegistrationDTO updateRegistration(Long id, EventRegistrationDTO dto) {

        EventRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        reg.setStatus(dto.getStatus());
        reg.setRelevanceScore(dto.getRelevanceScore());
        reg.setConfirmedAt(dto.getConfirmedAt());
        reg.setAttended(dto.getAttended());
        reg.setCertificateUrl(dto.getCertificateUrl());

        return mapToDTO(registrationRepository.save(reg));
    }

    @Override
    public void deleteRegistration(Long id) {
        registrationRepository.deleteById(id);
    }
    @Override
    public void registerToEvent(Long eventId, EventRegistration registration) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // ✅ Vérifier capacité
        if (event.getCurrentRegistrations() >= event.getMaxCapacity()) {
            throw new RuntimeException("Event is full");
        }

        // 🔗 associer l'événement
        registration.setEvent(event);

        // 💾 sauvegarder inscription
        registrationRepository.save(registration);

        // 🔥 incrémenter
        event.setCurrentRegistrations(event.getCurrentRegistrations() + 1);

        // 💾 sauvegarder event
        eventRepository.save(event);
    }
    public EventRegistration markAsAttended(Long userId, Long eventId) {

        EventRegistration reg = registrationRepository
                .findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        reg.setAttended(true);
        reg.setConfirmedAt(LocalDateTime.now());

        return registrationRepository.save(reg);
    }
}
