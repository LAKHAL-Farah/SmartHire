package tn.esprit.msinterview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.dto.AnswerEvaluationDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.AnswerEvaluation;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.service.AnswerEvaluationService;
import tn.esprit.msinterview.service.InterviewSessionService;
import tn.esprit.msinterview.service.SessionAnswerService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/evaluations")
@RequiredArgsConstructor
public class AnswerEvaluationController {

    private final AnswerEvaluationService evaluationService;
    private final SessionAnswerService sessionAnswerService;
    private final InterviewSessionService interviewSessionService;
    private final InterviewRequestUserResolver requestUserResolver;

    @PostMapping("/trigger/{answerId}")
    public ResponseEntity<Void> triggerEvaluation(HttpServletRequest request, @PathVariable Long answerId) {
        assertAnswerOwner(answerId, request);
        evaluationService.triggerEvaluation(answerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/answer/{answerId}")
    public ResponseEntity<AnswerEvaluationDTO> getEvaluationByAnswer(HttpServletRequest request, @PathVariable Long answerId) {
        assertAnswerOwner(answerId, request);
        AnswerEvaluation evaluation = evaluationService.getEvaluationByAnswer(answerId);
        return ResponseEntity.ok(DTOMapper.toEvaluationDTO(evaluation));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<AnswerEvaluationDTO>> getEvaluationsBySession(HttpServletRequest request, @PathVariable Long sessionId) {
        assertSessionOwner(sessionId, request);
        List<AnswerEvaluation> evaluations = evaluationService.getEvaluationsBySession(sessionId);
        return ResponseEntity.ok(evaluations.stream().map(DTOMapper::toEvaluationDTO).toList());
    }

    @PutMapping("/voice/{answerId}")
    public ResponseEntity<AnswerEvaluationDTO> updateVoiceScores(
            HttpServletRequest request,
            @PathVariable Long answerId,
            @RequestBody Map<String, Object> requestBody) {
        assertAnswerOwner(answerId, request);
        Double confidenceScore = ((Number) requestBody.get("confidenceScore")).doubleValue();
        Double toneScore = ((Number) requestBody.get("toneScore")).doubleValue();
        Double emotionScore = ((Number) requestBody.get("emotionScore")).doubleValue();
        Double speechRate = ((Number) requestBody.get("speechRate")).doubleValue();
        Integer hesitationCount = ((Number) requestBody.get("hesitationCount")).intValue();

        AnswerEvaluation evaluation = evaluationService.updateVoiceScores(answerId, confidenceScore, toneScore, emotionScore, speechRate, hesitationCount);
        return ResponseEntity.ok(DTOMapper.toEvaluationDTO(evaluation));
    }

    @PutMapping("/video/{sessionId}")
    public ResponseEntity<AnswerEvaluationDTO> updateVideoScores(
            HttpServletRequest request,
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> requestBody) {
        assertSessionOwner(sessionId, request);

        Long answerId = ((Number) requestBody.get("answerId")).longValue();
        assertAnswerOwner(answerId, request);

        Double postureScore = ((Number) requestBody.get("postureScore")).doubleValue();
        Double eyeContactScore = ((Number) requestBody.get("eyeContactScore")).doubleValue();
        Double expressionScore = ((Number) requestBody.get("expressionScore")).doubleValue();

        AnswerEvaluation evaluation = evaluationService.updateVideoScores(answerId, postureScore, eyeContactScore, expressionScore);
        return ResponseEntity.ok(DTOMapper.toEvaluationDTO(evaluation));
    }

    @PutMapping("/code/{answerId}")
    public ResponseEntity<AnswerEvaluationDTO> updateCodeScores(
            HttpServletRequest request,
            @PathVariable Long answerId,
            @RequestBody Map<String, Object> requestBody) {
        assertAnswerOwner(answerId, request);
        Double codeCorrectnessScore = ((Number) requestBody.get("codeCorrectnessScore")).doubleValue();
        String codeComplexityNote = (String) requestBody.get("codeComplexityNote");

        AnswerEvaluation evaluation = evaluationService.updateCodeScores(answerId, codeCorrectnessScore, codeComplexityNote);
        return ResponseEntity.ok(DTOMapper.toEvaluationDTO(evaluation));
    }

    private void assertSessionOwner(Long sessionId, HttpServletRequest request) {
        Long ownerUserId = interviewSessionService.getSessionById(sessionId).getUserId();
        requestUserResolver.assertCurrentUserOwnsUserId(ownerUserId, request, "Session");
    }

    private void assertAnswerOwner(Long answerId, HttpServletRequest request) {
        SessionAnswer answer = sessionAnswerService.getAnswerById(answerId);
        requestUserResolver.assertCurrentUserOwnsUserId(answer.getSession().getUserId(), request, "Answer");
    }
}
