package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.dto.PressureEventDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.PressureEvent;
import tn.esprit.msinterview.entity.enumerated.PressureEventType;
import tn.esprit.msinterview.service.PressureEventService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pressure")
@RequiredArgsConstructor
public class PressureEventController {

    private final PressureEventService pressureEventService;

    @PostMapping("/trigger")
    public ResponseEntity<PressureEventDTO> triggerPressureEvent(@RequestBody Map<String, Object> request) {
        Long sessionId = Long.valueOf(request.get("sessionId").toString());
        String eventTypeStr = (String) request.get("eventType");
        Long questionIdAtTrigger = request.get("questionIdAtTrigger") != null ? 
                Long.valueOf(request.get("questionIdAtTrigger").toString()) : null;
        
        PressureEventType eventType = PressureEventType.valueOf(eventTypeStr);
        PressureEvent event = pressureEventService.triggerPressureEvent(sessionId, eventType, questionIdAtTrigger);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(DTOMapper.toPressureEventDTO(event));
    }

    @PostMapping("/{eventId}/reaction")
    public ResponseEntity<Void> recordReaction(@PathVariable Long eventId, @RequestBody Map<String, Object> request) {
        boolean reacted = (boolean) request.get("reacted");
        long reactionTimeMs = Long.parseLong(request.get("reactionTimeMs").toString());
        
        pressureEventService.recordReaction(eventId, reacted, reactionTimeMs);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<PressureEventDTO>> getEventsForSession(@PathVariable Long sessionId) {
        List<PressureEvent> events = pressureEventService.getEventsForSession(sessionId);
        return ResponseEntity.ok(DTOMapper.toPressureEventDTOList(events));
    }

    @GetMapping("/session/{sessionId}/summary")
    public ResponseEntity<Map<String, Object>> getPressureSummary(@PathVariable Long sessionId) {
        Map<String, Object> summary = pressureEventService.getPressureSummary(sessionId);
        return ResponseEntity.ok(summary);
    }
}
