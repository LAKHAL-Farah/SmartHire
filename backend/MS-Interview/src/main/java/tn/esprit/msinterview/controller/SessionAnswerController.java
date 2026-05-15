package tn.esprit.msinterview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.dto.SessionAnswerDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.SessionAnswer;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;
import tn.esprit.msinterview.service.InterviewSessionService;
import tn.esprit.msinterview.service.SessionAnswerService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/answers")
@RequiredArgsConstructor
public class SessionAnswerController {

    private final SessionAnswerService answerService;
    private final InterviewSessionService interviewSessionService;
    private final InterviewRequestUserResolver requestUserResolver;

    @PostMapping("/submit")
    public ResponseEntity<SessionAnswerDTO> submitAnswer(HttpServletRequest httpRequest, @RequestBody Map<String, Object> request) {
        Long sessionId = ((Number) request.get("sessionId")).longValue();
        assertSessionOwner(sessionId, httpRequest);
        Long questionId = ((Number) request.get("questionId")).longValue();
        String answerText = (String) request.get("answerText");
        String videoUrl = (String) request.get("videoUrl");
        String audioUrl = (String) request.get("audioUrl");
        String codeAnswer = (String) request.get("codeAnswer");

        SessionAnswer answer = answerService.submitAnswer(sessionId, questionId, answerText, videoUrl, audioUrl, codeAnswer);
        return new ResponseEntity<>(DTOMapper.toAnswerDTO(answer), HttpStatus.ACCEPTED);
    }

    @PostMapping("/submit-code")
    public ResponseEntity<SessionAnswerDTO> submitCodeAnswer(HttpServletRequest httpRequest, @RequestBody Map<String, Object> request) {
        Long sessionId = ((Number) request.get("sessionId")).longValue();
        assertSessionOwner(sessionId, httpRequest);
        Long questionId = ((Number) request.get("questionId")).longValue();
        String code = (String) request.get("code");
        CodeLanguage language = CodeLanguage.valueOf((String) request.get("language"));

        SessionAnswer answer = answerService.submitCodeAnswer(sessionId, questionId, code, language);
        return new ResponseEntity<>(DTOMapper.toAnswerDTO(answer), HttpStatus.CREATED);
    }

    @PostMapping("/retry")
    public ResponseEntity<SessionAnswerDTO> retryAnswer(HttpServletRequest httpRequest, @RequestBody Map<String, Object> request) {
        Long sessionId = ((Number) request.get("sessionId")).longValue();
        assertSessionOwner(sessionId, httpRequest);
        Long questionId = ((Number) request.get("questionId")).longValue();
        String answerText = (String) request.get("answerText");
        String videoUrl = (String) request.get("videoUrl");
        String audioUrl = (String) request.get("audioUrl");
        String codeAnswer = (String) request.get("codeAnswer");

        SessionAnswer answer = answerService.retryAnswer(sessionId, questionId, answerText, videoUrl, audioUrl, codeAnswer);
        return ResponseEntity.ok(DTOMapper.toAnswerDTO(answer));
    }

    @PostMapping("/follow-up")
    public ResponseEntity<SessionAnswerDTO> submitFollowUpAnswer(HttpServletRequest httpRequest, @RequestBody Map<String, Object> request) {
        Long sessionId = ((Number) request.get("sessionId")).longValue();
        assertSessionOwner(sessionId, httpRequest);
        Long questionId = ((Number) request.get("questionId")).longValue();
        Long parentAnswerId = ((Number) request.get("parentAnswerId")).longValue();
        String answerText = (String) request.get("answerText");

        SessionAnswer answer = answerService.submitFollowUpAnswer(sessionId, questionId, parentAnswerId, answerText);
        return new ResponseEntity<>(DTOMapper.toAnswerDTO(answer), HttpStatus.CREATED);
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<SessionAnswerDTO>> getAnswersBySession(HttpServletRequest request, @PathVariable Long sessionId) {
        assertSessionOwner(sessionId, request);
        List<SessionAnswer> answers = answerService.getAnswersBySession(sessionId);
        return ResponseEntity.ok(DTOMapper.toAnswerDTOList(answers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionAnswerDTO> getAnswerById(HttpServletRequest request, @PathVariable Long id) {
        SessionAnswer answer = answerService.getAnswerById(id);
        requestUserResolver.assertCurrentUserOwnsUserId(answer.getSession().getUserId(), request, "Answer");
        return ResponseEntity.ok(DTOMapper.toAnswerDTO(answer));
    }

    @PostMapping(value = "/submit-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SessionAnswer> submitAudioAnswer(
            HttpServletRequest request,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam("questionId") Long questionId,
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "videoUrl", required = false) String videoUrl
    ) {
        assertSessionOwner(sessionId, request);
        log.debug("POST /answers/submit-audio - session={}, question={}, audioSize={}",
                sessionId, questionId, audioFile.getSize());

        SessionAnswer answer = answerService.submitAudioAnswer(
                sessionId, questionId, audioFile, videoUrl
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(answer);
    }

    @PostMapping(value = "/transcribe-only", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> transcribeOnly(
            @RequestParam("audio") MultipartFile audioFile
    ) {
        log.debug("POST /answers/transcribe-only - audioSize={}", audioFile.getSize());

        String transcript = answerService.transcribeAudio(audioFile);
        return ResponseEntity.ok(Map.of("transcript", transcript));
    }

    private void assertSessionOwner(Long sessionId, HttpServletRequest request) {
        Long ownerUserId = interviewSessionService.getSessionById(sessionId).getUserId();
        requestUserResolver.assertCurrentUserOwnsUserId(ownerUserId, request, "Session");
    }
}
