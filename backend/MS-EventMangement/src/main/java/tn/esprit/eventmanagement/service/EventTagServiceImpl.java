package tn.esprit.eventmanagement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.eventmanagement.DTO.tag.EventTagDTO;
import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.entities.EventTag;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.repository.EventTagRepository;

import java.util.ArrayList;
import java.util.List;

@Service

public class EventTagServiceImpl implements IEventTagService {

    private final EventTagRepository repository;
    private final EventRepository eventRepository;

    @Autowired
    public EventTagServiceImpl(EventTagRepository repository, EventRepository eventRepository) {
        this.repository = repository;
        this.eventRepository = eventRepository;
    }

    @Override
    public EventTag addTag(EventTagDTO dto) {
        EventTag tag = new EventTag();
        tag.setTagName(dto.getTagName());
        tag.setColor(dto.getColor());

        return repository.save(tag);
    }

    @Override
    public EventTag updateTag(Long id, EventTagDTO dto) {
        EventTag tag = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found"));

        tag.setTagName(dto.getTagName());
        tag.setColor(dto.getColor());

        return repository.save(tag);
    }

    @Override
    public void deleteTag(Long id) {
        repository.deleteById(id);
    }

    @Override
    public EventTag getTagById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found"));
    }

    @Override
    public List<EventTag> getAllTags() {
        return repository.findAll();
    }


    @Override
    public List<EventTag> getTagByEventId(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with id: " + eventId));
        return event.getTags();
    }
    @Override
    public EventTag assignTagToEvent(Long tagId, Long eventId) {
        EventTag tag = repository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("Tag not found"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if(tag.getEvents() == null) {
            tag.setEvents(new ArrayList<>());
        }
        tag.getEvents().add(event);

        if(event.getTags() == null) {
            event.setTags(new ArrayList<>());
        }
        event.getTags().add(tag);

        repository.save(tag); // ou eventRepository.save(event) selon ta logique
        return tag;
    }
}