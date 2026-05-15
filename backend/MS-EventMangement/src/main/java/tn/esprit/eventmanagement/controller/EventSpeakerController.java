package tn.esprit.eventmanagement.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.DTO.speaker.EventSpeakerDTO;
import tn.esprit.eventmanagement.entities.EventSpeaker;
import tn.esprit.eventmanagement.service.EventSpeakerServiceImpl;

import java.util.List;

@RestController
@RequestMapping("/api/speakers")
@CrossOrigin
public class EventSpeakerController {

    private final EventSpeakerServiceImpl speakerService;

    public EventSpeakerController(EventSpeakerServiceImpl speakerService) {
        this.speakerService = speakerService;
    }


    @GetMapping("/{id}")
    public ResponseEntity<EventSpeaker> getSpeaker(@PathVariable Long id) {
        return ResponseEntity.ok(speakerService.getSpeakerById(id));
    }

    @GetMapping
    public ResponseEntity<List<EventSpeaker>> getAllSpeakers() {
        return ResponseEntity.ok(speakerService.getAll());
    }


    @PostMapping
    public ResponseEntity<EventSpeaker> addSpeaker(@RequestBody EventSpeakerDTO dto) {
        return ResponseEntity.ok(speakerService.addSpeaker(dto));
    }


    @PutMapping("/{id}")
    public ResponseEntity<EventSpeaker> updateSpeaker(
            @PathVariable Long id,
            @RequestBody EventSpeakerDTO dto
    ) {
        return ResponseEntity.ok(speakerService.update(id, dto));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteSpeaker(@PathVariable Long id) {
        speakerService.delete(id);
        return ResponseEntity.ok("Speaker deleted successfully");
    }
}