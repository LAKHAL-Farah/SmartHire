package tn.esprit.eventmanagement.service;

import tn.esprit.eventmanagement.DTO.speaker.EventSpeakerDTO;
import tn.esprit.eventmanagement.entities.EventSpeaker;

import java.util.List;

public interface IEventSpeakerService {
    EventSpeaker addSpeaker(EventSpeakerDTO speaker);
    List<EventSpeaker> getAll();
    void delete(Long id);
    EventSpeaker update(Long id ,EventSpeakerDTO speakerDTO);
    EventSpeaker getSpeakerById(Long id );

}