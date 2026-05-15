package tn.esprit.msassessment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msassessment.ai.AssessmentAdviceService;
import tn.esprit.msassessment.ai.AiAdviceClient;
import tn.esprit.msassessment.dto.request.*;
import tn.esprit.msassessment.dto.response.*;
import tn.esprit.msassessment.entity.*;
import tn.esprit.msassessment.entity.enums.SessionStatus;
import tn.esprit.msassessment.exception.BusinessException;
import tn.esprit.msassessment.exception.ForbiddenException;
import tn.esprit.msassessment.exception.ResourceNotFoundException;
import tn.esprit.msassessment.integration.MsUserLookupClient;
import tn.esprit.msassessment.integration.UserServiceIntegrationClient;
import tn.esprit.msassessment.mapper.AssessmentPaperMapper;
import tn.esprit.msassessment.repository.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssessmentSessionService {

    /**
     * When true, candidates see their score immediately after submit — no admin publish step.
     * Integrity violations and forfeits always release immediately regardless of this flag.
     */
    @Value("${smarthire.assessment.auto-release-results:true}")
    private boolean autoReleaseResults;

    private final AssessmentSessionRepository sessionRepository;
    private final QuestionCategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;
    private final AnswerChoiceRepository answerChoiceRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final UserAssessmentAssignmentRepository assignmentRepository;
    private final TopicQuestionQueryService topicQuestionQueryService;
    private final AssessmentPaperMapper paperMapper;
    private final ObjectMapper objectMapper;
    private final CandidateAssignmentService candidateAssignmentService;
    private final UserServiceIntegrationClient userServiceIntegrationClient;
    private final MsUserLookupClient msUserLookupClient;
    private final SkillProfileService skillProfileService;
    private final AssessmentAdviceService adviceService;
    private final AiAdviceClient aiAdviceClient;

    // ── Start ──────────────────────────────────────────────────────────────

    public SessionResponse start(StartSessionRequest request) {
        candidateAssignmentService.assertMayStartSession(request.userId(), request.categoryId());
        QuestionCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));

        List<Question> pool = questionRepository.findByCategoryIdAndActiveIsTrueOrderByIdAsc(category.getId());
        if (pool.isEmpty()) {
            throw new BusinessException("This category has no active questions yet.");
        }

        String uid = request.userId().toString();
        if (sessionRepository.existsByUserIdAndCategory_Id(uid, category.getId())) {
            throw new ForbiddenException(
                    "You already have an attempt for this category. Each category allows a single attempt.");
        }

        AssessmentSession session = AssessmentSession.builder()
                .userId(uid)
                .category(category)
                .startedAt(Instant.now())
                .status(SessionStatus.IN_PROGRESS)
                .resultReleasedToCandidate(false)
                .candidateDisplayName(trimToNull(request.candidateDisplayName()))
                .build();

        String[] onboardingCtx = null;
        return toSessionResponse(sessionRepository.save(session), true, onboardingCtx);
    }

    public SessionResponse startByTopic(StartSessionByTopicRequest request) {
        candidateAssignmentService.assertMayStartTopicSession(request.userId());
        List<Question> picked = topicQuestionQueryService.pickRandomQuestionsWithChoices(request.topic(), request.count());
        if (picked.isEmpty()) {
            throw new BusinessException("No questions match topic \"" + request.topic() + "\".");
        }

        QuestionCategory category = picked.get(0).getCategory();
        String uid = request.userId().toString();
        if (sessionRepository.existsByUserIdAndCategory_Id(uid, category.getId())) {
            throw new ForbiddenException("You already have an attempt for this category.");
        }

        List<Long> ids = picked.stream().map(Question::getId).toList();
        String idsJson;
        try {
            idsJson = objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize question ids", e);
        }

        AssessmentSession session = AssessmentSession.builder()
                .userId(uid)
                .category(category)
                .topicTag(request.topic().trim())
                .selectedQuestionIdsJson(idsJson)
                .startedAt(Instant.now())
                .status(SessionStatus.IN_PROGRESS)
                .resultReleasedToCandidate(false)
                .candidateDisplayName(trimToNull(request.candidateDisplayName()))
                .build();

        String[] onboardingCtx = null;
        return toSessionResponse(sessionRepository.save(session), true, onboardingCtx);
    }

    // ── Integrity violation ────────────────────────────────────────────────
    /**
     * Called when the client detects the candidate left the quiz (tab hidden, window minimized).
     * Flags the session AND immediately closes it with score 0 — no submit step needed.
     */
    public SessionResponse reportIntegrityViolation(Long sessionId, IntegrityViolationRequest request) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            // Already closed — return current state
            return toSessionResponse(session, true, resolveOnboardingContext(session.getUserId()));
        }

        session.setIntegrityViolation(true);
        session.setIntegrityViolationAt(Instant.now());

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim() : "tab hidden / window minimized";
        String note = "[Integrity] Candidate left the quiz interface (" + reason + "). Score forced to 0.";
        appendNote(session, note);

        // Force close immediately at 0 — candidate cannot continue
        session.getAnswers().clear();
        session.setScorePercent(0);
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setResultReleasedToCandidate(true); // integrity violations are always visible immediately

        session = sessionRepository.save(session);
        String[] ctx = resolveOnboardingContext(session.getUserId());
        userServiceIntegrationClient.notifyResultPublished(session);
        skillProfileService.refreshForUser(UUID.fromString(session.getUserId()));
        return toSessionResponse(session, true, ctx);
    }

    // ── Forfeit ────────────────────────────────────────────────────────────
    /**
     * Candidate clicks "Back to assessments" without submitting.
     * Session closes at 0%, flagged, result visible immediately.
     */
    public SessionResponse forfeit(Long sessionId, ForfeitSessionRequest request) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (!session.getUserId().equals(request.userId())) {
            throw new ForbiddenException("This attempt belongs to another user.");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessException("This session is already finished.");
        }

        session.setIntegrityViolation(true);
        session.setIntegrityViolationAt(Instant.now());
        session.setForfeit(true);
        session.getAnswers().clear();
        appendNote(session, "[Forfeit] Candidate left via back/return without submitting. Score 0; visible immediately.");
        session.setScorePercent(0);
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setResultReleasedToCandidate(true); // forfeit results are always visible immediately

        session = sessionRepository.save(session);
        String[] ctx = resolveOnboardingContext(session.getUserId());
        userServiceIntegrationClient.notifyResultPublished(session);
        skillProfileService.refreshForUser(UUID.fromString(session.getUserId()));
        return toSessionResponse(session, true, ctx);
    }

    // ── Read ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SessionResponse getById(Long id) {
        AssessmentSession session = sessionRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        return toSessionResponse(session, true, resolveOnboardingContext(session.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listForUser(UUID userId) {
        String[] ctx = resolveOnboardingContext(userId.toString());
        return sessionRepository.findByUserIdOrderByStartedAtDesc(userId.toString()).stream()
                .map(s -> toSessionResponse(s, true, ctx))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listPendingReleaseForAdmin() {
        List<AssessmentSession> rows = sessionRepository
                .findByStatusAndResultReleasedToCandidateIsFalseOrderByCompletedAtDesc(SessionStatus.COMPLETED);
        Map<String, String> nameCache = new HashMap<>();
        return rows.stream().map(s -> toSessionResponse(s, false, nameCache)).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listAllCompletedForAdmin() {
        List<AssessmentSession> rows = sessionRepository.findByStatusOrderByCompletedAtDesc(SessionStatus.COMPLETED);
        Map<String, String> nameCache = new HashMap<>();
        return rows.stream().map(s -> toSessionResponse(s, false, nameCache)).toList();
    }

    // ── Paper ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public QuestionPaperResponse getPaper(Long sessionId) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessException("This session is not open for questions.");
        }
        List<Question> questions = resolveQuestionsForSession(session);
        List<QuestionPaperItem> items = questions.stream().map(paperMapper::toPaperItem).toList();
        QuestionCategory cat = session.getCategory();
        return new QuestionPaperResponse(session.getId(), cat.getId(), cat.getTitle(), items);
    }

    // ── Submit ─────────────────────────────────────────────────────────────

    public SessionResponse submit(Long sessionId, SubmitAnswersRequest request) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessException("Session already closed.");
        }

        List<Question> questions = resolveQuestionsForSession(session);
        Set<Long> expected = questions.stream().map(Question::getId).collect(Collectors.toSet());

        List<AnswerSelection> selections = request.selections();
        if (selections.size() != expected.size()) {
            throw new BusinessException("Answer each question exactly once.");
        }

        Set<Long> seen = new HashSet<>();
        for (AnswerSelection sel : selections) {
            if (!seen.add(sel.questionId())) throw new BusinessException("Duplicate question in submission.");
            if (!expected.contains(sel.questionId())) throw new BusinessException("Question does not belong to this assessment.");
        }
        if (!seen.equals(expected)) throw new BusinessException("Missing one or more questions.");

        Map<Long, Question> byId = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        session.getAnswers().clear();

        int maxPoints = questions.stream().mapToInt(Question::getPoints).sum();
        int earned = 0;
        Instant now = Instant.now();

        for (AnswerSelection sel : selections) {
            Question q = byId.get(sel.questionId());
            AnswerChoice choice = answerChoiceRepository.findById(sel.answerChoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Answer choice not found: " + sel.answerChoiceId()));
            if (!choice.getQuestion().getId().equals(q.getId())) {
                throw new BusinessException("Choice does not match question.");
            }
            boolean ok = choice.isCorrect();
            int pts = ok ? q.getPoints() : 0;
            earned += pts;
            session.getAnswers().add(SessionAnswer.builder()
                    .session(session).question(q).selectedChoice(choice)
                    .correct(ok).pointsEarned(pts).answeredAt(now).build());
        }

        int scorePercent = maxPoints == 0 ? 0 : (int) Math.round(earned * 100.0 / maxPoints);

        // Integrity violation: force 0 regardless of answers
        if (session.isIntegrityViolation()) {
            for (SessionAnswer a : session.getAnswers()) { a.setPointsEarned(0); a.setCorrect(false); }
            scorePercent = 0;
            appendNote(session, "[Integrity] Final score set to 0 — candidate left the quiz interface during the attempt.");
        }

        session.setScorePercent(scorePercent);
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(now);

        if (request.notes() != null && !request.notes().isBlank()) {
            appendNote(session, request.notes().trim());
        }

        // Always release immediately (integrity, forfeit, or auto-release)
        session.setResultReleasedToCandidate(autoReleaseResults || session.isIntegrityViolation());

        session = sessionRepository.save(session);
        String[] ctx = resolveOnboardingContext(session.getUserId());

        if (session.isResultReleasedToCandidate()) {
            userServiceIntegrationClient.notifyResultPublished(session);
            skillProfileService.refreshForUser(UUID.fromString(session.getUserId()));
        }
        return toSessionResponse(session, true, ctx);
    }

    // ── Release (admin) ────────────────────────────────────────────────────

    public SessionResponse releaseResultToCandidate(Long sessionId, ReleaseSessionResultRequest request) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BusinessException("Only completed assessments can be released.");
        }
        if (session.isResultReleasedToCandidate()) {
            throw new BusinessException("Results were already released to the candidate.");
        }
        session.setResultReleasedToCandidate(true);
        if (request != null && request.feedbackToCandidate() != null) {
            session.setAdminFeedbackToCandidate(request.feedbackToCandidate());
        }
        if (request != null && request.adminNote() != null) {
            appendNote(session, "[Admin] " + request.adminNote());
        }
        session = sessionRepository.save(session);
        String[] ctx = resolveOnboardingContext(session.getUserId());
        userServiceIntegrationClient.notifyResultPublished(session);
        skillProfileService.refreshForUser(UUID.fromString(session.getUserId()));
        Map<String, String> emptyNameCache = null;
        return toSessionResponse(session, false, emptyNameCache);
    }

    // ── Review ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SessionResultResponse getReview(Long sessionId, UUID userId) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (userId != null && !session.getUserId().equals(userId.toString())) {
            throw new BusinessException("This attempt belongs to another user.");
        }
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BusinessException("Results are available only after the session is completed.");
        }
        if (!session.isResultReleasedToCandidate()) {
            throw new BusinessException("Your administrator has not published the results for this attempt yet.");
        }
        List<SessionAnswer> rows = sessionAnswerRepository.findDetailBySession(sessionId);
        String[] ctx = resolveOnboardingContext(session.getUserId());
        return new SessionResultResponse(toSessionResponse(session, true, ctx), buildAnswerReviewItems(rows));
    }

    @Transactional(readOnly = true)
    public SessionResultResponse getAdminSessionReview(Long sessionId) {
        AssessmentSession session = sessionRepository.findWithCategoryById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BusinessException("This session is not completed yet.");
        }
        List<SessionAnswer> rows = sessionAnswerRepository.findDetailBySession(sessionId);
        Map<String, String> emptyNameCache = null;
        return new SessionResultResponse(toSessionResponse(session, false, emptyNameCache), buildAnswerReviewItems(rows));
    }

    // ── Admin: delete session ─────────────────────────────────────────────

    public void deleteSession(Long sessionId) {
        AssessmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new BusinessException("Only completed sessions can be deleted.");
        }
        sessionRepository.delete(session);
    }

    // ── Admin: scores by user ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserScoresSummaryResponse getScoresByUser(UUID userId) {
        String uid = userId.toString();
        List<AssessmentSession> sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(uid).stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .toList();

        String displayName = msUserLookupClient.findDisplayName(userId).orElse(null);
        String situation = null;
        String careerPath = null;
        var assignment = assignmentRepository.findByUserId(uid).orElse(null);
        if (assignment != null) {
            situation = assignment.getSituation();
            careerPath = assignment.getCareerPath();
        }

        int avg = sessions.isEmpty() ? 0 :
                (int) Math.round(sessions.stream()
                        .mapToInt(s -> s.getScorePercent() != null ? s.getScorePercent() : 0)
                        .average().orElse(0));

        List<UserScoresSummaryResponse.SessionScoreRow> rows = sessions.stream()
                .map(s -> new UserScoresSummaryResponse.SessionScoreRow(
                        s.getId(),
                        s.getCategory().getTitle(),
                        s.getCategory().getCode(),
                        s.getTopicTag(),
                        s.getScorePercent(),
                        s.isResultReleasedToCandidate(),
                        s.isIntegrityViolation(),
                        s.getCompletedAt()))
                .toList();

        return new UserScoresSummaryResponse(uid, displayName, situation, careerPath, avg, rows);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private List<AnswerReviewItem> buildAnswerReviewItems(List<SessionAnswer> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(a -> a.getQuestion().getId()))
                .map(this::toAnswerReviewItem)
                .toList();
    }

    private AnswerReviewItem toAnswerReviewItem(SessionAnswer r) {
        Question q = r.getQuestion();
        AnswerChoice selected = r.getSelectedChoice();
        AnswerChoice correct = q.getChoices().stream()
                .filter(AnswerChoice::isCorrect)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Question " + q.getId() + " has no correct choice"));
        return new AnswerReviewItem(q.getId(), q.getPrompt(), q.getDifficulty().name(), q.getPoints(),
                selected.getId(), selected.getLabel(), correct.getId(), correct.getLabel(),
                r.isCorrect(), r.getPointsEarned());
    }

    private List<Question> resolveQuestionsForSession(AssessmentSession session) {
        String json = session.getSelectedQuestionIdsJson();
        if (json != null && !json.isBlank()) {
            try {
                List<Long> ids = objectMapper.readValue(json, new TypeReference<>() {});
                if (ids.isEmpty()) return List.of();
                List<Question> loaded = questionRepository.findByIdInWithChoices(ids);
                Map<Long, Question> map = loaded.stream().collect(Collectors.toMap(Question::getId, q -> q));
                return ids.stream().map(map::get).filter(Objects::nonNull).toList();
            } catch (Exception e) {
                throw new BusinessException("Invalid session question selection state.");
            }
        }
        return questionRepository.findByCategoryIdAndActiveIsTrueOrderByIdAsc(session.getCategory().getId());
    }

    // ── toSessionResponse overloads ────────────────────────────────────────

    /** Candidate view with onboarding context for advice. ctx[0]=situation, ctx[1]=careerPath */
    private SessionResponse toSessionResponse(AssessmentSession s, boolean candidateView, String[] ctx) {
        Map<String, String> nameCache = null;
        return toSessionResponse(s, candidateView, ctx, nameCache);
    }

    /** Admin view with name cache. */
    private SessionResponse toSessionResponse(AssessmentSession s, boolean candidateView, Map<String, String> nameCache) {
        String[] onboardingCtx = null;
        return toSessionResponse(s, candidateView, onboardingCtx, nameCache);
    }

    private SessionResponse toSessionResponse(AssessmentSession s, boolean candidateView,
                                               String[] onboardingCtx, Map<String, String> nameCache) {
        QuestionCategory cat = s.getCategory();
        boolean released = s.isResultReleasedToCandidate();

        Integer score = s.getScorePercent();
        if (candidateView && s.getStatus() == SessionStatus.COMPLETED && !released) score = null;

        String adminFeedback = s.getAdminFeedbackToCandidate();
        if (candidateView && !released) adminFeedback = null;

        String notes = s.getNotes();
        if (candidateView && s.getStatus() == SessionStatus.COMPLETED && !released) notes = null;

        String candidateDisplayName = null;
        if (!candidateView) {
            candidateDisplayName = trimToNull(s.getCandidateDisplayName());
            if (candidateDisplayName == null) {
                candidateDisplayName = resolveCandidateDisplayName(UUID.fromString(s.getUserId()), nameCache);
            }
        }

        // Generate advice only when score is visible to the candidate
        List<String> advice = null;
        if (candidateView && s.getStatus() == SessionStatus.COMPLETED && released) {
            String situation = onboardingCtx != null && onboardingCtx.length > 0 ? onboardingCtx[0] : null;
            String careerPath = onboardingCtx != null && onboardingCtx.length > 1 ? onboardingCtx[1] : null;
            try {
                advice = aiAdviceClient.getAdvice(s, situation, careerPath);
            } catch (Exception e) {
                log.warn("Advice generation failed for session {}: {}", s.getId(), e.getMessage());
            }
        }

        return new SessionResponse(
                s.getId(), s.getUserId(), cat.getId(), cat.getTitle(), cat.getCode(),
                s.getTopicTag(), s.getStartedAt(), s.getCompletedAt(), s.getStatus(),
                score, released, released, notes, adminFeedback, candidateDisplayName,
                s.isIntegrityViolation(), s.isForfeit(), advice);
    }

    private String[] resolveOnboardingContext(String userId) {
        try {
            return assignmentRepository.findByUserId(userId)
                    .map(a -> new String[]{a.getSituation(), a.getCareerPath()})
                    .orElse(new String[]{null, null});
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }

    private String resolveCandidateDisplayName(UUID userId, Map<String, String> nameCache) {
        if (nameCache != null) {
            String key = userId.toString();
            if (nameCache.containsKey(key)) return nameCache.get(key);
            String resolved = msUserLookupClient.findDisplayName(userId).orElse(null);
            nameCache.put(key, resolved);
            return resolved;
        }
        return msUserLookupClient.findDisplayName(userId).orElse(null);
    }

    private static void appendNote(AssessmentSession session, String note) {
        String prev = session.getNotes();
        session.setNotes(prev != null && !prev.isBlank() ? prev + "\n" + note : note);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
