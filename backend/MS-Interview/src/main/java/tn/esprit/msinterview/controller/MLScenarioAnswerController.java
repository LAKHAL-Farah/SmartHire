package tn.esprit.msinterview.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.dto.MLScenarioAnswerDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.MLScenarioAnswer;
import tn.esprit.msinterview.service.MLScenarioAnswerService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ml-answers")
@RequiredArgsConstructor
public class MLScenarioAnswerController {

    private final MLScenarioAnswerService mlScenarioAnswerService;

    @PostMapping("/extract/{answerId}")
    public ResponseEntity<MLScenarioAnswerDTO> extractMLConcepts(@PathVariable Long answerId,
                                                             @RequestBody Map<String, String> request) {
        String transcript = request.get("transcript");
        MLScenarioAnswer mlAnswer = mlScenarioAnswerService.extractAndSave(answerId, transcript);
        return ResponseEntity.status(HttpStatus.CREATED).body(DTOMapper.toMLScenarioDTO(mlAnswer));
    }

    @GetMapping("/answer/{answerId}")
    public ResponseEntity<MLScenarioAnswerDTO> getMLAnswerByAnswer(@PathVariable Long answerId) {
        MLScenarioAnswer mlAnswer = mlScenarioAnswerService.getByAnswer(answerId);
        return ResponseEntity.ok(DTOMapper.toMLScenarioDTO(mlAnswer));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<MLScenarioAnswerDTO>> getMLAnswersBySession(@PathVariable Long sessionId) {
        List<MLScenarioAnswer> mlAnswers = mlScenarioAnswerService.getBySession(sessionId);
        return ResponseEntity.ok(mlAnswers.stream().map(DTOMapper::toMLScenarioDTO).toList());
    }

    @GetMapping("/answer/{answerId}/followup")
    public ResponseEntity<String> generateFollowUp(@PathVariable Long answerId) {
        String followUp = mlScenarioAnswerService.generateFollowUp(answerId);
        return ResponseEntity.ok(followUp);
    }
}
