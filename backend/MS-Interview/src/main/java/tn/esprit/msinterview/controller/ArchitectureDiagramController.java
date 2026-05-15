package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.dto.ArchitectureDiagramDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.ArchitectureDiagram;
import tn.esprit.msinterview.service.ArchitectureDiagramService;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/diagrams")
@RequiredArgsConstructor
public class ArchitectureDiagramController {

    private final ArchitectureDiagramService architectureDiagramService;

    public record SubmitDiagramRequest(Long answerId, Long sessionId, Long questionId, String diagramJson) {}

    public record ExplainDiagramRequest(Long sessionId, Long questionId, String explanation) {}

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitDiagram(@RequestBody SubmitDiagramRequest request) {
        ArchitectureDiagram diagram = architectureDiagramService.submitDiagram(
                request.answerId(),
                request.sessionId(),
                request.questionId(),
                request.diagramJson()
        );
        architectureDiagramService.evaluateDiagram(diagram.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("diagramId", diagram.getId());
        response.put("status", "accepted");
        response.put("answerId", request.answerId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{diagramId}/explain")
    public ResponseEntity<Map<String, Object>> explainDiagram(
            @PathVariable Long diagramId,
            @RequestBody ExplainDiagramRequest request
    ) {
        ArchitectureDiagram diagram = architectureDiagramService.explainDiagram(
                diagramId,
                request.sessionId(),
                request.questionId(),
                request.explanation()
        );
        architectureDiagramService.evaluateDiagram(diagram.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("diagramId", diagram.getId());
        response.put("status", "accepted");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/answer/{answerId}")
    public ResponseEntity<ArchitectureDiagramDTO> getDiagramByAnswer(@PathVariable Long answerId) {
        ArchitectureDiagram diagram = architectureDiagramService.getDiagramByAnswer(answerId);
        return ResponseEntity.ok(DTOMapper.toArchitectureDTO(diagram));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<ArchitectureDiagramDTO>> getDiagramsBySession(@PathVariable Long sessionId) {
        List<ArchitectureDiagram> diagrams = architectureDiagramService.getDiagramsBySession(sessionId);
        return ResponseEntity.ok(diagrams.stream().map(DTOMapper::toArchitectureDTO).toList());
    }
}
