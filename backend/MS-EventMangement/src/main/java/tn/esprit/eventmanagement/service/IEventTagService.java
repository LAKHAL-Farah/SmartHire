package tn.esprit.eventmanagement.service;

import tn.esprit.eventmanagement.DTO.tag.EventTagDTO;
import tn.esprit.eventmanagement.entities.EventSpeaker;
import tn.esprit.eventmanagement.entities.EventTag;

import java.util.List;

public interface IEventTagService {

    EventTag addTag(EventTagDTO dto);

    EventTag updateTag(Long id, EventTagDTO dto);

    void deleteTag(Long id);

    EventTag getTagById(Long id);

    List<EventTag> getAllTags();

    List<EventTag> getTagByEventId(Long aLong);
    EventTag assignTagToEvent(Long tagId, Long eventId);


}