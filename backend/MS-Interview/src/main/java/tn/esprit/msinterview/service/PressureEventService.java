package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.PressureEvent;
import tn.esprit.msinterview.entity.enumerated.PressureEventType;

import java.util.List;
import java.util.Map;

public interface PressureEventService {
    PressureEvent triggerPressureEvent(Long sessionId, PressureEventType eventType, Long questionIdAtTrigger);
    void recordReaction(Long eventId, boolean reacted, long reactionTimeMs);
    List<PressureEvent> getEventsForSession(Long sessionId);
    Map<String, Object> getPressureSummary(Long sessionId);
    void schedulePressureEvents(Long sessionId);
}
