package tn.esprit.eventmanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.DTO.speaker.EventSpeakerDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventSpeaker;
import tn.esprit.eventmanagement.entities.EventTag;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.repository.EventSpeakerRepository;

import java.security.PublicKey;
import java.util.List;

@Service
public class EventSpeakerServiceImpl implements IEventSpeakerService {

    @Autowired
    private EventSpeakerRepository repository;
    private EventRepository    eventRepository;

    public EventSpeakerServiceImpl(EventSpeakerRepository repository, EventRepository eventRepository) {
        this.repository = repository;
        this.eventRepository = eventRepository;
    }

    @Override
    public EventSpeaker addSpeaker(EventSpeakerDTO dto) {

        EventSpeaker speaker = new EventSpeaker();

        // ✅ Setter les données D'ABORD
        speaker.setFirstName(dto.getFirstName());
        speaker.setLastName(dto.getLastName());
        speaker.setBio(dto.getBio());
        speaker.setExpertise(dto.getExpertise());
        speaker.setCompany(dto.getCompany());
        speaker.setLinkedinUrl(dto.getLinkedinUrl());

        // ✅ Générer l'avatar APRÈS — firstName et lastName sont maintenant disponibles
        String avatarUrl = generateAvatar(speaker.getFirstName(), speaker.getLastName());
        speaker.setPhotoUrl(avatarUrl);

        Event event = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));

        speaker.setEvent(event);

        return repository.save(speaker);
    }

    @Override
    public List<EventSpeaker> getAll() {
        return repository.findAll();
    }

    @Override
    public void delete(Long id){
        repository.deleteById(id);
    }

    @Override
    public EventSpeaker update(Long id , EventSpeakerDTO dto){
        EventSpeaker speaker=repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Speaker not found"));


        speaker.setFirstName(dto.getFirstName());
        speaker.setLastName(dto.getLastName());
        speaker.setBio(dto.getBio());
        speaker.setExpertise(dto.getExpertise());
        speaker.setCompany(dto.getCompany());
        speaker.setLinkedinUrl(dto.getLinkedinUrl());
        speaker.setPhotoUrl(dto.getPhotoUrl());
        return repository.save(speaker);

    }
    private String generateAvatar(String firstName, String lastName) {
        String seed = (firstName + lastName).toLowerCase().replaceAll("\\s+", "");
        return "https://api.dicebear.com/7.x/avataaars/svg?seed=" + seed
                + "&backgroundColor=b6e3f4,c0aede,d1d4f9";
    }
    @Override
    public EventSpeaker  getSpeakerById(Long id ){
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Speaker not found"));
    }
}