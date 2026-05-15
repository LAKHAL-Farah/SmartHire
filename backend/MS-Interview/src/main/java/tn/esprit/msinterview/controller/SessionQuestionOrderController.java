package tn.esprit.msinterview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.dto.SessionQuestionOrderDTO;
import tn.esprit.msinterview.dto.InterviewQuestionDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.SessionQuestionOrder;
import tn.esprit.msinterview.service.InterviewSessionService;
import tn.esprit.msinterview.service.SessionQuestionOrderService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/questions")
@RequiredArgsConstructor
public class SessionQuestionOrderController {

    private final SessionQuestionOrderService questionOrderService;
    private final TTSClient ttsClient;
    private final InterviewSessionService interviewSessionService;
    private final InterviewRequestUserResolver requestUserResolver;

    @GetMapping
    public ResponseEntity<List<SessionQuestionOrderDTO>> getOrderedQuestions(HttpServletRequest request, @PathVariable Long sessionId) {
        assertSessionOwner(sessionId, request);
        List<SessionQuestionOrder> questions = questionOrderService.getOrderedQuestionsForSession(sessionId);
        return ResponseEntity.ok(questions.stream().map(DTOMapper::toSessionQuestionOrderDTO).toList());
    }

    @GetMapping("/current")
    public ResponseEntity<InterviewQuestionDTO> getCurrentQuestion(HttpServletRequest request, @PathVariable Long sessionId) {
        assertSessionOwner(sessionId, request);
        InterviewQuestion question = questionOrderService.getCurrentQuestion(sessionId);
        return ResponseEntity.ok(toQuestionDTOWithTts(sessionId, question));
    }

    @GetMapping("/next")
    public ResponseEntity<InterviewQuestionDTO> getNextQuestion(HttpServletRequest request, @PathVariable Long sessionId) {
        assertSessionOwner(sessionId, request);
        Optional<InterviewQuestion> nextQuestion = questionOrderService.advanceToNextQuestion(sessionId);
        return nextQuestion
                .map(question -> ResponseEntity.ok(toQuestionDTOWithTts(sessionId, question)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/skip")
    public ResponseEntity<Void> skipQuestion(HttpServletRequest request, @PathVariable Long sessionId) {
        assertSessionOwner(sessionId, request);
        questionOrderService.skipCurrentQuestion(sessionId);
        return ResponseEntity.noContent().build();
    }

    private void assertSessionOwner(Long sessionId, HttpServletRequest request) {
        Long ownerUserId = interviewSessionService.getSessionById(sessionId).getUserId();
        requestUserResolver.assertCurrentUserOwnsUserId(ownerUserId, request, "Session");
    }

    private InterviewQuestionDTO toQuestionDTOWithTts(Long sessionId, InterviewQuestion question) {
        InterviewQuestionDTO dto = DTOMapper.toQuestionDTO(question);
        dto.setTtsAudioUrl(
                ttsClient.resolveQuestionAudioUrl(sessionId, question.getId(), question.getQuestionText())
        );
        return dto;
    }
}
