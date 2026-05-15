package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.AnswerSubmissionDto;
import tn.esprit.msroadmap.DTO.response.AnswerResultDto;
import tn.esprit.msroadmap.DTO.response.InterviewResultDto;
import tn.esprit.msroadmap.DTO.response.InterviewSessionDto;
import tn.esprit.msroadmap.DTO.response.QuestionDto;
import tn.esprit.msroadmap.Services.InterviewService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final CurrentUserIdResolver currentUserIdResolver;

    @PostMapping("/session")
    public ResponseEntity<InterviewSessionDto> createSession(
            @RequestParam(required = false) Long userId,
            @RequestParam String careerPath,
            @RequestParam String difficulty) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(interviewService.createSession(resolvedUserId, careerPath, difficulty));
    }

    @GetMapping("/session/{sessionId}/question")
    public ResponseEntity<QuestionDto> getNextQuestion(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getNextQuestion(sessionId));
    }

    @PostMapping("/session/{sessionId}/answer")
    public ResponseEntity<AnswerResultDto> submitAnswer(
            @PathVariable Long sessionId,
            @RequestBody AnswerSubmissionDto answer) {
        return ResponseEntity.ok(interviewService.submitAnswer(sessionId, answer));
    }

    @GetMapping("/session/{sessionId}/score")
    public ResponseEntity<InterviewResultDto> getScore(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getSessionScore(sessionId));
    }

    @GetMapping("/user/{userId}/sessions")
    public ResponseEntity<List<InterviewSessionDto>> getUserSessions(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(interviewService.getUserSessions(resolvedUserId));
    }
}
