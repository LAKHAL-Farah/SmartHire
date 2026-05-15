package tn.esprit.eventmanagement.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.DTO.tag.EventTagDTO;
import tn.esprit.eventmanagement.entities.EventTag;
import tn.esprit.eventmanagement.service.IEventTagService;

import java.util.List;

@RestController
@RequestMapping("/api/event-tags")

public class EventTagController {

    private final IEventTagService service;

    public EventTagController(IEventTagService service) {
        this.service = service;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<EventTag> addTag(@RequestBody EventTagDTO dto) {
        return ResponseEntity.ok(service.addTag(dto));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<EventTag> updateTag(@PathVariable Long id,
                                              @RequestBody EventTagDTO dto) {
        return ResponseEntity.ok(service.updateTag(id, dto));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        service.deleteTag(id);
        return ResponseEntity.noContent().build();
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<EventTag> getTag(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTagById(id));
    }

    // GET ALL
    @GetMapping
    public ResponseEntity<List<EventTag>> getAllTags() {
        return ResponseEntity.ok(service.getAllTags());
    }
    @GetMapping("/{id}/tags")
    public List<EventTag> getAllTagsByEventId(@PathVariable("id") Long eventId) {
        return service.getTagByEventId(eventId);
    }

    @PostMapping("/{tagId}/assign-to-event/{eventId}")
    public ResponseEntity<EventTag> assignTagToEvent(@PathVariable Long tagId, @PathVariable Long eventId) {
        EventTag updatedTag = service.assignTagToEvent(tagId, eventId);
        return ResponseEntity.ok(updatedTag);
    }
}