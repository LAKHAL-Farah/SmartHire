package tn.esprit.msinterview.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.PressureEvent;
import tn.esprit.msinterview.entity.enumerated.PressureEventType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.PressureEventRepository;
import tn.esprit.msinterview.service.PressureEventService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PressureEventServiceImpl implements PressureEventService {

    private final PressureEventRepository pressureEventRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    @Transactional
    public PressureEvent triggerPressureEvent(Long sessionId, PressureEventType eventType, Long questionIdAtTrigger) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        PressureEvent event = PressureEvent.builder()
                .session(session)
                .eventType(eventType)
                .questionIdAtTrigger(questionIdAtTrigger)
                .triggeredAt(LocalDateTime.now())
                .candidateReacted(false)
                .reactionTimeMs(null)
                .build();

        PressureEvent saved = pressureEventRepository.save(event);
        
        // Increment pressure events counter in session
        int currentCount = session.getPressureEventsTriggered() == null ? 0 : session.getPressureEventsTriggered();
        session.setPressureEventsTriggered(currentCount + 1);
        interviewSessionRepository.save(session);
        
        // TODO: Push PRESSURE_EVENT WebSocket event to frontend
        log.info("Pressure event triggered: sessionId={}, eventType={}", sessionId, eventType);
        
        return saved;
    }

    @Override
    @Transactional
    public void recordReaction(Long eventId, boolean reacted, long reactionTimeMs) {
        PressureEvent event = pressureEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Pressure event not found: " + eventId));

        event.setCandidateReacted(reacted);
        event.setReactionTimeMs(reactionTimeMs);
        
        pressureEventRepository.save(event);
        log.info("Reaction recorded: eventId={}, reacted={}, reactionTimeMs={}", eventId, reacted, reactionTimeMs);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PressureEvent> getEventsForSession(Long sessionId) {
        return pressureEventRepository.findBySessionIdOrderByTriggeredAtAsc(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPressureSummary(Long sessionId) {
        Map<String, Object> summary = new HashMap<>();
        
        long totalEvents = pressureEventRepository.countBySessionId(sessionId);
        long reactedCount = pressureEventRepository.countBySessionIdAndCandidateReacted(sessionId, true);
        Double avgReactionTime = pressureEventRepository.findAvgReactionTime(sessionId);

        summary.put("totalEvents", totalEvents);
        summary.put("reactedCount", reactedCount);
        summary.put("falterCount", totalEvents - reactedCount);
        summary.put("avgReactionTimeMs", avgReactionTime != null ? avgReactionTime : 0.0);
        summary.put("composurePercentage", totalEvents > 0 ? (reactedCount * 100) / totalEvents : 0);

        return summary;
    }

    @Async
    @Scheduled(cron = "0 */2 * * * *")
    public void schedulePressureEventsTick() {
        interviewSessionRepository.findAll().stream()
                .filter(InterviewSession::isPressureMode)
                .filter(session -> session.getStatus() == SessionStatus.IN_PROGRESS)
                .forEach(session -> {
                    try {
                        schedulePressureEvents(session.getId());
                    } catch (Exception ex) {
                        log.warn("Pressure event scheduling failed for session {}", session.getId(), ex);
                    }
                });
    }

    @Override
    public void schedulePressureEvents(Long sessionId) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Check if session is still in progress
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            return;
        }

        // TODO: Implement smart pressure event scheduling
        // - Consider current question context
        // - Vary timing to prevent predictability
        // - Different event types (INTERRUPT, TIMER_SHRINK, FOLLOWUP_MID)
        
        // Placeholder: trigger random event
        PressureEventType[] eventTypes = PressureEventType.values();
        PressureEventType randomType = eventTypes[(int) (Math.random() * eventTypes.length)];
        
        triggerPressureEvent(sessionId, randomType, null);
    }
}
