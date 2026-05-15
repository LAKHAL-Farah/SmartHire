package tn.esprit.eventmanagement.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.eventmanagement.DTO.event.EventDTO;

import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.service.EventService;
import tn.esprit.eventmanagement.service.EventServiceImpl;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
@CrossOrigin
@RestController
@RequestMapping("/api/events")

public class EventController {

    private final EventServiceImpl eventService;
    private  final EventRepository eventRepository;

    public EventController(EventServiceImpl eventService, EventRepository eventRepository) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
    }


    @PostMapping
    public ResponseEntity<EventDTO> createEvent(@RequestBody EventDTO eventDTO) {
        return new ResponseEntity<>(eventService.createEvent(eventDTO), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @GetMapping
    public ResponseEntity<List<EventDTO>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDTO> updateEvent(@PathVariable Long id, @RequestBody EventDTO eventDTO) {
        return ResponseEntity.ok(eventService.updateEvent(id, eventDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{eventId}/register/{userId}")
    public ResponseEntity<String> register(
            @PathVariable Long eventId,
            @PathVariable Long userId) {

        eventService.registerToEvent(eventId, userId);
        return ResponseEntity.ok("Registered successfully");
    }
    @PostMapping("/{id}/ai-summary")
    public ResponseEntity<Event> generateAiSummary(@PathVariable Long id) {
        Event updated = eventService.generateAndSaveAiSummary(id);
        return ResponseEntity.ok(updated);
    }
    @GetMapping("/{id}/qrcode")
    public ResponseEntity<byte[]> getQRCode(@PathVariable Long id) throws Exception {

        // 1. Récupérer event
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // 2. URL vers page inscription Angular ✅
        String url = "http://localhost:4200/register/event/" + event.getId();

        // 3. Génération QR
        int width = 300;
        int height = 300;

        BitMatrix matrix = new MultiFormatWriter()
                .encode(url, BarcodeFormat.QR_CODE, width, height);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", stream);

        // 4. Retour image
        return ResponseEntity.ok()
                .header("Content-Type", "image/png")
                .body(stream.toByteArray());
    }
    @GetMapping("/{id}/calendar")
    public ResponseEntity<byte[]> downloadCalendar(@PathVariable Long id) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        String icsContent = "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + event.getId() + "\n" +
                "DTSTAMP:" + formatDate(event.getStartDate()) + "\n" +
                "DTSTART:" + formatDate(event.getStartDate()) + "\n" +
                "DTEND:" + formatDate(event.getEndDate()) + "\n" +
                "SUMMARY:" + event.getTitle() + "\n" +
                "DESCRIPTION:" + event.getDescription() + "\n" +
                "LOCATION:" + event.getLocation() + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=event.ics")
                .header("Content-Type", "text/calendar")
                .body(icsContent.getBytes());
    }
    private String formatDate(LocalDateTime date) {
        return date.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
    }
}