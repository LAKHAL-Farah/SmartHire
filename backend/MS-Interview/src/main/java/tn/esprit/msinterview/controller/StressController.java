package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msinterview.dto.StressPayload;
import tn.esprit.msinterview.service.StressAggregatorService;

@RestController
@RequestMapping("/api/v1/stress")
@RequiredArgsConstructor
@Slf4j
public class StressController {

    private final SimpMessagingTemplate ws;
    private final StressAggregatorService stressAggregator;

    @PostMapping("/{sessionId}")
    public ResponseEntity<Void> updateStress(
            @PathVariable Long sessionId,
            @RequestBody StressPayload payload) {

        log.debug("Stress update: session={} score={} level={}",
                sessionId, payload.stressScore(), payload.level());

        stressAggregator.recordReading(sessionId, payload);
        ws.convertAndSend("/topic/stress/" + sessionId, payload);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/question/{questionId}/finalize")
    public ResponseEntity<StressAggregatorService.StressQuestionSummary> finalizeQuestion(
            @PathVariable Long sessionId,
            @PathVariable Long questionId) {

        StressAggregatorService.StressQuestionSummary summary =
                stressAggregator.finalizeQuestion(sessionId, questionId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{sessionId}/summary")
    public ResponseEntity<StressAggregatorService.StressSessionSummary> getSessionSummary(
            @PathVariable Long sessionId) {

        return ResponseEntity.ok(stressAggregator.getSessionSummary(sessionId));
    }
}
