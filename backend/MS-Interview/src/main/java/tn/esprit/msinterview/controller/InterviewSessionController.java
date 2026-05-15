package tn.esprit.msinterview.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.ai.LiveGreetingBuilder;
import tn.esprit.msinterview.ai.TTSClient;
import tn.esprit.msinterview.dto.InterviewSessionDTO;
import tn.esprit.msinterview.dto.InterviewQuestionDTO;
import tn.esprit.msinterview.dto.LiveSessionStartRequest;
import tn.esprit.msinterview.dto.LiveSessionStartResponse;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.SessionQuestionOrder;
import tn.esprit.msinterview.entity.enumerated.InterviewMode;
import tn.esprit.msinterview.entity.enumerated.InterviewType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.repository.SessionQuestionOrderRepository;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.service.InterviewSessionService;
import tn.esprit.msinterview.service.SessionQuestionOrderService;
import tn.esprit.msinterview.websocket.SessionEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Slf4j
public class InterviewSessionController {

    private final InterviewSessionService sessionService;
    private final SessionQuestionOrderService questionOrderService;
    private final SessionQuestionOrderRepository questionOrderRepository;
    private final TTSClient ttsClient;
    private final LiveGreetingBuilder liveGreetingBuilder;
    private final SessionEventPublisher sessionEventPublisher;
    private final InterviewRequestUserResolver requestUserResolver;

    @Value("${smarthire.audio.base-url:/interview-service/api/v1/audio}")
    private String audioBaseUrl;

    @PostMapping("/start")
    public ResponseEntity<InterviewSessionDTO> startSession(
            HttpServletRequest request,
            @RequestBody(required = false) StartSessionRequest body,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long careerPathId,
            @RequestParam(required = false) RoleType role,
            @RequestParam(required = false) InterviewMode mode,
            @RequestParam(required = false) InterviewType type,
            @RequestParam(required = false) Integer questionCount) {

        Long requestedUserId = body != null && body.userId() != null ? body.userId() : userId;
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(requestedUserId, request);
        Long resolvedCareerPathId = body != null && body.careerPathId() != null ? body.careerPathId() : careerPathId;
        RoleType resolvedRole = resolveRole(body, role);
        InterviewMode resolvedMode = body != null && body.mode() != null ? body.mode() : mode;
        InterviewType resolvedType = body != null && body.type() != null ? body.type() : type;
        Integer resolvedQuestionCount = body != null && body.questionCount() != null ? body.questionCount() : questionCount;

        if (resolvedCareerPathId == null || resolvedRole == null || resolvedMode == null || resolvedType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Missing required fields: careerPathId, roleType(or role), mode, type");
        }

        InterviewSession session = sessionService.startSession(
                resolvedUserId,
                resolvedCareerPathId,
                resolvedRole,
                resolvedMode,
                resolvedType,
                resolvedQuestionCount
        );

        InterviewSessionDTO dto = DTOMapper.toSessionDTO(session);
        InterviewQuestionDTO firstQuestionDto = null;
        try {
            InterviewQuestion currentQuestion = questionOrderService.getCurrentQuestion(session.getId());
            firstQuestionDto = DTOMapper.toQuestionDTO(currentQuestion);
            dto.setTtsAudioUrl(
                ttsClient.resolveQuestionAudioUrl(
                    session.getId(),
                    currentQuestion.getId(),
                    currentQuestion.getQuestionText()
                )
            );
            firstQuestionDto.setTtsAudioUrl(dto.getTtsAudioUrl());
        } catch (Exception ignored) {
            dto.setTtsAudioUrl(null);
        }

        LinkedHashMap<String, Object> wsPayload = new LinkedHashMap<>();
        wsPayload.put("session", dto);
        wsPayload.put("roleType", dto.getRoleType());
        wsPayload.put("firstQuestion", firstQuestionDto);
        wsPayload.put("ttsAudioUrl", dto.getTtsAudioUrl());
        sessionEventPublisher.pushSessionStarted(session.getId(), wsPayload);

        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    private RoleType resolveRole(StartSessionRequest body, RoleType requestParamRole) {
        if (body == null) {
            return requestParamRole;
        }
        if (body.roleType() != null) {
            return body.roleType();
        }
        if (body.role() != null) {
            return body.role();
        }
        return requestParamRole;
    }

    public record StartSessionRequest(
            Long userId,
            Long careerPathId,
            RoleType roleType,
            RoleType role,
            InterviewMode mode,
            InterviewType type,
            Integer questionCount
    ) {}

    @PostMapping("/start-live")
    public ResponseEntity<LiveSessionStartResponse> startLive(
            HttpServletRequest httpRequest,
            @Valid @RequestBody LiveSessionStartRequest request) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(request.userId(), httpRequest);
        LiveSessionStartRequest normalizedRequest = new LiveSessionStartRequest(
                resolvedUserId,
                request.careerPathId(),
                request.liveSubMode(),
                request.questionCount(),
                request.companyName(),
                request.targetRole()
        );

        log.info("[SessionController] POST /start-live: userId={}", resolvedUserId);
        LiveSessionStartResponse response = sessionService.startLiveSession(normalizedRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/live-bootstrap")
    public ResponseEntity<Map<String, Object>> getLiveBootstrap(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String candidateName
    ) {
        InterviewSession session = sessionService.getSessionById(id);
        assertSessionOwner(session, request);

        int currentIndex = session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex();
        Optional<SessionQuestionOrder> currentOrder = questionOrderRepository.findBySessionIdAndQuestionOrder(id, currentIndex);
        if (currentOrder.isEmpty()) {
            currentOrder = questionOrderRepository.findBySessionIdAndQuestionOrder(id, 0);
            currentIndex = 0;
        }

        if (currentOrder.isEmpty() || currentOrder.get().getQuestion() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ordered question found for this live session.");
        }

        InterviewQuestion firstQuestion = currentOrder.get().getQuestion();
        int totalQuestions = session.getQuestionCountRequested() != null
                ? session.getQuestionCountRequested()
                : (int) questionOrderRepository.countBySessionId(id);

        String resolvedCompany = companyName == null || companyName.isBlank() ? "Tech Company" : companyName.trim();
        String resolvedRole = targetRole == null || targetRole.isBlank() ? "Candidate" : targetRole.trim();
        String resolvedCandidate = candidateName == null || candidateName.isBlank() ? "Candidate" : candidateName.trim();

        String greetingText = liveGreetingBuilder.buildGreeting(session, resolvedCompany, resolvedRole, firstQuestion);
        String greetingAudioUrl = resolveGreetingAudioUrl(greetingText, firstQuestion.getQuestionText());

        log.info("[LiveBootstrap] session={} candidate={} greetingAudioPresent={}",
                id, resolvedCandidate, greetingAudioUrl != null && !greetingAudioUrl.isBlank());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", id);
        payload.put("greetingAudioUrl", greetingAudioUrl == null ? "" : greetingAudioUrl);
        payload.put("firstQuestionId", firstQuestion.getId());
        payload.put("firstQuestionText", firstQuestion.getQuestionText());
        payload.put("totalQuestions", totalQuestions);
        payload.put("currentQuestionIndex", currentIndex);
        payload.put("liveSubMode", session.getLiveSubMode() == null ? "TEST_LIVE" : session.getLiveSubMode().name());
        payload.put("candidateName", resolvedCandidate);
        payload.put("companyName", resolvedCompany);
        payload.put("targetRole", resolvedRole);

        return ResponseEntity.ok(payload);
    }

    private String buildAudioUrl(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }

        String normalizedBase = audioBaseUrl == null || audioBaseUrl.isBlank()
                ? "/interview-service/api/v1/audio"
                : audioBaseUrl.trim();

        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }

        return normalizedBase + "/" + filename;
    }

    private String resolveGreetingAudioUrl(String greetingText, String firstQuestionText) {
        String filename = ttsClient.synthesize(greetingText);
        if (filename != null && !filename.isBlank()) {
            return buildAudioUrl(filename);
        }

        String safeQuestion = firstQuestionText == null ? "" : firstQuestionText.trim();
        if (safeQuestion.length() > 220) {
            safeQuestion = safeQuestion.substring(0, 220);
        }

        String fallback = safeQuestion.isBlank()
                ? "Welcome. Let us begin the interview."
                : "Welcome. First question: " + safeQuestion;

        String fallbackFilename = ttsClient.synthesize(fallback);
        if (fallbackFilename == null || fallbackFilename.isBlank()) {
            return "";
        }

        return buildAudioUrl(fallbackFilename);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewSessionDTO> getSessionById(HttpServletRequest request, @PathVariable Long id) {
        InterviewSession session = sessionService.getSessionById(id);
        assertSessionOwner(session, request);
        return ResponseEntity.ok(DTOMapper.toSessionDTO(session));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<InterviewSessionDTO>> getSessionsByUser(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        List<InterviewSession> sessions = sessionService.getSessionsByUser(resolvedUserId);
        return ResponseEntity.ok(sessions.stream().map(DTOMapper::toSessionDTO).toList());
    }

    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<List<InterviewSessionDTO>> getSessionsByUserAndStatus(
            HttpServletRequest request,
            @PathVariable Long userId,
            @PathVariable SessionStatus status) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        List<InterviewSession> sessions = sessionService.getSessionsByUserAndStatus(resolvedUserId, status);
        return ResponseEntity.ok(sessions.stream().map(DTOMapper::toSessionDTO).toList());
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<?> getActiveSession(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        Optional<InterviewSession> session = sessionService.getActiveSession(resolvedUserId);
        return session.map(s -> ResponseEntity.ok(DTOMapper.toSessionDTO(s)))
                .orElseGet(() -> ResponseEntity.ok().body(null));
    }

    @PutMapping("/{id}/pause")
    public ResponseEntity<InterviewSessionDTO> pauseSession(HttpServletRequest request, @PathVariable Long id) {
        assertSessionOwner(sessionService.getSessionById(id), request);
        InterviewSession session = sessionService.pauseSession(id);
        return ResponseEntity.ok(DTOMapper.toSessionDTO(session));
    }

    @PutMapping("/{id}/resume")
    public ResponseEntity<InterviewSessionDTO> resumeSession(HttpServletRequest request, @PathVariable Long id) {
        assertSessionOwner(sessionService.getSessionById(id), request);
        InterviewSession session = sessionService.resumeSession(id);
        return ResponseEntity.ok(DTOMapper.toSessionDTO(session));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<InterviewSessionDTO> completeSession(HttpServletRequest request, @PathVariable Long id) {
        assertSessionOwner(sessionService.getSessionById(id), request);
        InterviewSession session = sessionService.completeSession(id);
        return ResponseEntity.ok(DTOMapper.toSessionDTO(session));
    }

    @PutMapping("/{id}/abandon")
    public ResponseEntity<InterviewSessionDTO> abandonSession(HttpServletRequest request, @PathVariable Long id) {
        assertSessionOwner(sessionService.getSessionById(id), request);
        InterviewSession session = sessionService.abandonSession(id);
        return ResponseEntity.ok(DTOMapper.toSessionDTO(session));
    }

    private void assertSessionOwner(InterviewSession session, HttpServletRequest request) {
        if (session == null || session.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found.");
        }
        requestUserResolver.assertCurrentUserOwnsUserId(session.getUserId(), request, "Session");
    }
}
