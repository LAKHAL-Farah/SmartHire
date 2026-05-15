package tn.esprit.msinterview.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.dto.InterviewQuestionDTO;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.repository.AnswerEvaluationRepository;
import tn.esprit.msinterview.repository.InterviewQuestionRepository;
import tn.esprit.msinterview.repository.InterviewReportRepository;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.InterviewStreakRepository;
import tn.esprit.msinterview.repository.SessionAnswerRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin/interview")
@RequiredArgsConstructor
public class AdminInterviewController {

    private static final String QUESTION_INTELLIGENCE_SELECT = """
        SELECT
            q.id AS questionId,
            q.question_text AS questionText,
            q.role_type AS roleType,
            q.type AS questionType,
            COALESCE((SELECT COUNT(*) FROM session_answers sa WHERE sa.question_id = q.id), 0) AS timesAsked,
            COALESCE((
                SELECT AVG(ae.overall_score)
                FROM answer_evaluations ae
                JOIN session_answers sa2 ON sa2.id = ae.answer_id
                WHERE sa2.question_id = q.id
            ), 0) AS avgScore,
            COALESCE((
                SELECT AVG(LENGTH(COALESCE(sa3.answer_text, '')))
                FROM session_answers sa3
                WHERE sa3.question_id = q.id
            ), 0) AS avgAnswerLength,
            COALESCE((
                SELECT CASE
                    WHEN COUNT(*) = 0 THEN 0
                    ELSE SUM(CASE WHEN sqo.was_skipped = TRUE THEN 1 ELSE 0 END) * 1.0 / COUNT(*)
                END
                FROM session_question_orders sqo
                WHERE sqo.question_id = q.id
            ), 0) AS skipRate
        FROM interview_questions q
        """;

    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final AnswerEvaluationRepository answerEvaluationRepository;
    private final InterviewStreakRepository interviewStreakRepository;
    private final EntityManager entityManager;

    @GetMapping("/stats/overview")
    public OverviewStatsResponse getOverviewStats() {
        long totalSessions = interviewSessionRepository.count();
        long sessionsToday = countSessionsSince(LocalDate.now().atStartOfDay());
        long sessionsThisWeek = countSessionsSince(LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay());
        long completedSessions = countSessionsByStatus(SessionStatus.COMPLETED);
        long abandonedSessions = countSessionsByStatus(SessionStatus.ABANDONED);
        double completionRate = totalSessions == 0 ? 0.0 : round1((completedSessions * 100.0) / totalSessions);

        long totalCandidates = defaultLong(entityManager.createQuery(
                "SELECT COUNT(DISTINCT s.userId) FROM InterviewSession s", Long.class).getSingleResult());
        long activeCandidatesNow = countSessionsByStatus(SessionStatus.IN_PROGRESS);

        double avgSessionScore = round1(defaultDouble(entityManager.createQuery(
                "SELECT AVG(r.finalScore) FROM InterviewReport r", Double.class).getSingleResult()));
        long avgSessionDuration = calculateAverageSessionDurationSeconds();

        long totalQuestions = interviewQuestionRepository.count();
        long activeQuestions = defaultLong(entityManager.createQuery(
                "SELECT COUNT(q) FROM InterviewQuestion q WHERE q.isActive = true", Long.class).getSingleResult());

        long totalAnswers = sessionAnswerRepository.count();
        double avgAnswerScore = round1(defaultDouble(entityManager.createQuery(
                "SELECT AVG(ae.overallScore) FROM AnswerEvaluation ae", Double.class).getSingleResult()));

        long totalStreaks = interviewStreakRepository.count();
        double avgStreakLength = round1(defaultDouble(entityManager.createQuery(
                "SELECT AVG(s.currentStreak) FROM InterviewStreak s", Double.class).getSingleResult()));
        long topStreakAllTime = defaultLong(entityManager.createQuery(
                "SELECT MAX(s.currentStreak) FROM InterviewStreak s", Integer.class).getSingleResult());

        return new OverviewStatsResponse(
                totalSessions,
                sessionsToday,
                sessionsThisWeek,
                completedSessions,
                abandonedSessions,
                completionRate,
                totalCandidates,
                activeCandidatesNow,
                avgSessionScore,
                avgSessionDuration,
                totalQuestions,
                activeQuestions,
                totalAnswers,
                avgAnswerScore,
                totalStreaks,
                avgStreakLength,
                topStreakAllTime
        );
    }

    @GetMapping("/stats/sessions-over-time")
    public List<SessionsOverTimePoint> getSessionsOverTime(@RequestParam(defaultValue = "30") int days) {
        int safeDays = clamp(days, 1, 365);
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);

        Query query = entityManager.createNativeQuery("""
            SELECT
                DATE(started_at) AS day_date,
                COUNT(*) AS total_count,
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
                SUM(CASE WHEN status = 'ABANDONED' THEN 1 ELSE 0 END) AS abandoned_count
            FROM interview_sessions
            WHERE started_at >= :fromDate
            GROUP BY DATE(started_at)
            ORDER BY DATE(started_at)
            """);
        query.setParameter("fromDate", Timestamp.valueOf(startDate.atStartOfDay()));

        Map<LocalDate, SessionsOverTimePoint> byDate = new HashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            LocalDate date = toLocalDate(row[0]);
            if (date == null) {
                continue;
            }
            byDate.put(date, new SessionsOverTimePoint(
                    date.toString(),
                    toLong(row[1]),
                    toLong(row[2]),
                    toLong(row[3])));
        }

        List<SessionsOverTimePoint> points = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = startDate.plusDays(i);
            points.add(byDate.getOrDefault(date, new SessionsOverTimePoint(date.toString(), 0L, 0L, 0L)));
        }
        return points;
    }

    @GetMapping("/stats/scores-over-time")
    public List<ScoresOverTimePoint> getScoresOverTime(@RequestParam(defaultValue = "30") int days) {
        int safeDays = clamp(days, 1, 365);
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);

        Query query = entityManager.createNativeQuery("""
            SELECT
                DATE(s.started_at) AS day_date,
                AVG(ae.overall_score) AS avg_score,
                AVG(ae.content_score) AS avg_content,
                AVG(ae.clarity_score) AS avg_clarity
            FROM interview_sessions s
            JOIN session_answers sa ON sa.session_id = s.id
            JOIN answer_evaluations ae ON ae.answer_id = sa.id
            WHERE s.started_at >= :fromDate
            GROUP BY DATE(s.started_at)
            ORDER BY DATE(s.started_at)
            """);
        query.setParameter("fromDate", Timestamp.valueOf(startDate.atStartOfDay()));

        Map<LocalDate, ScoresOverTimePoint> byDate = new HashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            LocalDate date = toLocalDate(row[0]);
            if (date == null) {
                continue;
            }

            byDate.put(date, new ScoresOverTimePoint(
                    date.toString(),
                    round1(toDouble(row[1])),
                    round1(toDouble(row[2])),
                    round1(toDouble(row[3]))
            ));
        }

        List<ScoresOverTimePoint> points = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = startDate.plusDays(i);
            points.add(byDate.getOrDefault(date, new ScoresOverTimePoint(date.toString(), 0.0, 0.0, 0.0)));
        }
        return points;
    }

    @GetMapping("/stats/by-role")
    public List<RoleBreakdownPoint> getByRole() {
        Query query = entityManager.createNativeQuery("""
            SELECT
                s.role_type,
                COUNT(*) AS total_count,
                AVG(r.final_score) AS avg_score,
                CASE WHEN COUNT(*) = 0 THEN 0
                     ELSE SUM(CASE WHEN s.status = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)
                END AS completion_rate
            FROM interview_sessions s
            LEFT JOIN interview_reports r ON r.session_id = s.id
            GROUP BY s.role_type
            """);

        Map<String, RoleBreakdownPoint> map = new HashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            String role = toStringValue(row[0]);
            if (role == null) {
                continue;
            }
            map.put(role, new RoleBreakdownPoint(
                    role,
                    toLong(row[1]),
                    round1(toDouble(row[2])),
                    round1(toDouble(row[3]))
            ));
        }

        List<RoleBreakdownPoint> response = new ArrayList<>();
        for (RoleType roleType : List.of(RoleType.SE, RoleType.CLOUD, RoleType.AI, RoleType.ALL)) {
            String key = roleType.name();
            response.add(map.getOrDefault(key, new RoleBreakdownPoint(key, 0L, 0.0, 0.0)));
        }
        return response;
    }

    @GetMapping("/stats/by-type")
    public List<TypeBreakdownPoint> getByType() {
        Query query = entityManager.createNativeQuery("""
            SELECT
                q.type,
                COUNT(sa.id) AS answer_count,
                AVG(ae.overall_score) AS avg_score
            FROM interview_questions q
            LEFT JOIN session_answers sa ON sa.question_id = q.id
            LEFT JOIN answer_evaluations ae ON ae.answer_id = sa.id
            GROUP BY q.type
            """);

        Map<String, TypeBreakdownPoint> map = new HashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            String type = toStringValue(row[0]);
            if (type == null) {
                continue;
            }
            map.put(type, new TypeBreakdownPoint(type, toLong(row[1]), round1(toDouble(row[2]))));
        }

        List<TypeBreakdownPoint> response = new ArrayList<>();
        for (QuestionType type : QuestionType.values()) {
            response.add(map.getOrDefault(type.name(), new TypeBreakdownPoint(type.name(), 0L, 0.0)));
        }
        return response;
    }

    @GetMapping("/stats/by-difficulty")
    public List<DifficultyBreakdownPoint> getByDifficulty() {
        Query query = entityManager.createNativeQuery("""
            SELECT
                q.difficulty,
                COUNT(sa.id) AS answer_count,
                AVG(ae.overall_score) AS avg_score,
                AVG(sa.time_spent_seconds) AS avg_time
            FROM interview_questions q
            LEFT JOIN session_answers sa ON sa.question_id = q.id
            LEFT JOIN answer_evaluations ae ON ae.answer_id = sa.id
            GROUP BY q.difficulty
            """);

        Map<String, DifficultyBreakdownPoint> map = new HashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            String difficulty = toStringValue(row[0]);
            if (difficulty == null) {
                continue;
            }
            map.put(difficulty, new DifficultyBreakdownPoint(
                    difficulty,
                    toLong(row[1]),
                    round1(toDouble(row[2])),
                    round1(toDouble(row[3]))
            ));
        }

        List<DifficultyBreakdownPoint> response = new ArrayList<>();
        for (DifficultyLevel difficulty : DifficultyLevel.values()) {
            response.add(map.getOrDefault(
                    difficulty.name(),
                    new DifficultyBreakdownPoint(difficulty.name(), 0L, 0.0, 0.0)
            ));
        }
        return response;
    }

    @GetMapping("/stats/score-distribution")
    public List<ScoreDistributionPoint> getScoreDistribution() {
        return List.of(
                new ScoreDistributionPoint("0-2", countReportsByScoreRange(0.0, 2.0, false)),
                new ScoreDistributionPoint("2-4", countReportsByScoreRange(2.0, 4.0, false)),
                new ScoreDistributionPoint("4-6", countReportsByScoreRange(4.0, 6.0, false)),
                new ScoreDistributionPoint("6-8", countReportsByScoreRange(6.0, 8.0, false)),
                new ScoreDistributionPoint("8-10", countReportsByScoreRange(8.0, 10.0, true))
        );
    }

    @GetMapping("/stats/questions/most-asked")
    public List<QuestionIntelligencePoint> getMostAskedQuestions(@RequestParam(defaultValue = "10") int limit) {
        return fetchQuestionIntelligence("timesAsked DESC, avgScore DESC", limit);
    }

    @GetMapping("/stats/questions/hardest")
    public List<QuestionIntelligencePoint> getHardestQuestions(@RequestParam(defaultValue = "10") int limit) {
        return fetchQuestionIntelligence("avgScore ASC, timesAsked DESC", limit);
    }

    @GetMapping("/stats/questions/longest-answers")
    public List<QuestionIntelligencePoint> getLongestAnswersQuestions(@RequestParam(defaultValue = "10") int limit) {
        return fetchQuestionIntelligence("avgAnswerLength DESC, timesAsked DESC", limit);
    }

    @GetMapping("/stats/questions/best-performing")
    public List<QuestionIntelligencePoint> getBestPerformingQuestions(@RequestParam(defaultValue = "10") int limit) {
        return fetchQuestionIntelligence("avgScore DESC, timesAsked DESC", limit);
    }

    @GetMapping("/stats/leaderboard")
    public List<LeaderboardPoint> getLeaderboard(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = clamp(limit, 1, 100);

        Query query = entityManager.createNativeQuery("""
            SELECT
                s.user_id AS userId,
                COUNT(*) AS totalSessions,
                COALESCE(AVG(r.final_score), 0) AS avgScore,
                COALESCE(MAX(r.final_score), 0) AS bestScore,
                COALESCE((SELECT st.current_streak FROM interview_streaks st WHERE st.user_id = s.user_id), 0) AS currentStreak,
                COALESCE((
                    SELECT COUNT(*)
                    FROM session_answers sa
                    JOIN interview_sessions sx ON sx.id = sa.session_id
                    WHERE sx.user_id = s.user_id
                ), 0) AS totalAnswers,
                COALESCE((
                    SELECT sx2.role_type
                    FROM interview_sessions sx2
                    WHERE sx2.user_id = s.user_id
                    GROUP BY sx2.role_type
                    ORDER BY COUNT(*) DESC
                    LIMIT 1
                ), 'ALL') AS topRole
            FROM interview_sessions s
            LEFT JOIN interview_reports r ON r.session_id = s.id
            GROUP BY s.user_id
            ORDER BY avgScore DESC, bestScore DESC, totalSessions DESC
            """);
        query.setMaxResults(safeLimit);

        List<LeaderboardPoint> points = new ArrayList<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            points.add(new LeaderboardPoint(
                    toLong(row[0]),
                    toLong(row[1]),
                    round1(toDouble(row[2])),
                    round1(toDouble(row[3])),
                    toLong(row[4]),
                    toLong(row[5]),
                    defaultString(row[6], "ALL")
            ));
        }
        return points;
    }

    @GetMapping("/stats/streaks/top")
    public List<TopStreakPoint> getTopStreaks(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = clamp(limit, 1, 100);

        Query query = entityManager.createNativeQuery("""
            SELECT
                user_id,
                current_streak,
                longest_streak,
                total_sessions_completed
            FROM interview_streaks
            ORDER BY longest_streak DESC, current_streak DESC
            """);
        query.setMaxResults(safeLimit);

        List<TopStreakPoint> points = new ArrayList<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            points.add(new TopStreakPoint(
                    toLong(row[0]),
                    toLong(row[1]),
                    toLong(row[2]),
                    toLong(row[3])
            ));
        }
        return points;
    }

    @GetMapping("/questions")
    public QuestionsPageResponse getQuestions(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        RoleType roleFilter = parseRole(role);
        QuestionType typeFilter = parseQuestionType(type);
        DifficultyLevel difficultyFilter = parseDifficulty(difficulty);

        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 100);
        String normalizedSearch = normalizeSearch(search);

        List<InterviewQuestion> all = interviewQuestionRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));

        List<InterviewQuestion> filtered = all.stream()
                .filter(q -> roleFilter == null || q.getRoleType() == roleFilter)
                .filter(q -> typeFilter == null || q.getType() == typeFilter)
                .filter(q -> difficultyFilter == null || q.getDifficulty() == difficultyFilter)
                .filter(q -> active == null || q.isActive() == active)
                .filter(q -> matchesSearch(q, normalizedSearch))
                .toList();

        long totalElements = filtered.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) safeSize);
        int fromIndex = safePage * safeSize;
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        List<InterviewQuestionDTO> content;
        if (fromIndex >= filtered.size()) {
            content = List.of();
        } else {
            content = filtered.subList(fromIndex, toIndex)
                    .stream()
                    .map(DTOMapper::toQuestionDTO)
                    .toList();
        }

        boolean first = safePage == 0;
        boolean last = totalPages == 0 || safePage >= totalPages - 1;

        return new QuestionsPageResponse(content, totalElements, totalPages, safePage, safeSize, first, last);
    }

    @GetMapping("/questions/{id}")
    public InterviewQuestionDTO getQuestionById(@PathVariable Long id) {
        InterviewQuestion question = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: " + id));
        return DTOMapper.toQuestionDTO(question);
    }

    @PostMapping("/questions")
    @Transactional
    public ResponseEntity<InterviewQuestionDTO> createQuestion(@RequestBody QuestionUpsertRequest request) {
        validateQuestionPayload(request);

        InterviewQuestion question = new InterviewQuestion();
        applyQuestionPayload(question, request, true);

        InterviewQuestion created = interviewQuestionRepository.save(question);
        return ResponseEntity.status(HttpStatus.CREATED).body(DTOMapper.toQuestionDTO(created));
    }

    @PutMapping("/questions/{id}")
    @Transactional
    public InterviewQuestionDTO updateQuestion(
            @PathVariable Long id,
            @RequestBody QuestionUpsertRequest request) {
        validateQuestionPayload(request);

        InterviewQuestion existing = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: " + id));

        applyQuestionPayload(existing, request, false);
        InterviewQuestion updated = interviewQuestionRepository.save(existing);
        return DTOMapper.toQuestionDTO(updated);
    }

    @DeleteMapping("/questions/{id}")
    @Transactional
    public ResponseEntity<Void> softDeleteQuestion(@PathVariable Long id) {
        InterviewQuestion existing = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: " + id));
        existing.setActive(false);
        interviewQuestionRepository.save(existing);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/questions/{id}/toggle-active")
    @Transactional
    public InterviewQuestionDTO toggleQuestionActive(@PathVariable Long id) {
        InterviewQuestion existing = interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: " + id));
        existing.setActive(!existing.isActive());
        InterviewQuestion updated = interviewQuestionRepository.save(existing);
        return DTOMapper.toQuestionDTO(updated);
    }

    @GetMapping("/questions/coverage")
    public List<QuestionCoveragePoint> getQuestionCoverage() {
        Query query = entityManager.createNativeQuery("""
            SELECT
                role_type,
                type,
                COUNT(*) AS total_count,
                SUM(CASE WHEN is_active = TRUE THEN 1 ELSE 0 END) AS active_count
            FROM interview_questions
            GROUP BY role_type, type
            """);

        Map<String, QuestionCoveragePoint> map = new HashMap<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            String role = defaultString(row[0], "ALL");
            String type = defaultString(row[1], "TECHNICAL");
            map.put(role + "::" + type, new QuestionCoveragePoint(
                    role,
                    type,
                    toLong(row[2]),
                    toLong(row[3])
            ));
        }

        List<QuestionCoveragePoint> response = new ArrayList<>();
        for (RoleType roleType : RoleType.values()) {
            for (QuestionType questionType : QuestionType.values()) {
                String key = roleType.name() + "::" + questionType.name();
                response.add(map.getOrDefault(
                        key,
                        new QuestionCoveragePoint(roleType.name(), questionType.name(), 0L, 0L)
                ));
            }
        }
        return response;
    }

    private List<QuestionIntelligencePoint> fetchQuestionIntelligence(String orderBy, int limit) {
        int safeLimit = clamp(limit, 1, 100);
        String sql = QUESTION_INTELLIGENCE_SELECT + " ORDER BY " + orderBy;
        Query query = entityManager.createNativeQuery(sql);
        query.setMaxResults(safeLimit);

        List<QuestionIntelligencePoint> points = new ArrayList<>();
        for (Object rowObj : query.getResultList()) {
            Object[] row = (Object[]) rowObj;
            points.add(new QuestionIntelligencePoint(
                    toLong(row[0]),
                    defaultString(row[1], ""),
                    defaultString(row[2], "ALL"),
                    defaultString(row[3], "TECHNICAL"),
                    toLong(row[4]),
                    round1(toDouble(row[5])),
                    round1(toDouble(row[6])),
                    round4(toDouble(row[7]))
            ));
        }
        return points;
    }

    private long countSessionsByStatus(SessionStatus status) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(s) FROM InterviewSession s WHERE s.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
        return defaultLong(count);
    }

    private long countSessionsSince(LocalDateTime fromDateTime) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(s) FROM InterviewSession s WHERE s.startedAt >= :fromDate", Long.class)
                .setParameter("fromDate", fromDateTime)
                .getSingleResult();
        return defaultLong(count);
    }

    private long calculateAverageSessionDurationSeconds() {
        List<InterviewSession> sessions = entityManager.createQuery(
                "SELECT s FROM InterviewSession s WHERE s.startedAt IS NOT NULL AND s.endedAt IS NOT NULL",
                InterviewSession.class
        ).getResultList();

        double average = sessions.stream()
                .filter(s -> s.getStartedAt() != null && s.getEndedAt() != null)
                .mapToLong(s -> Math.max(0L, Duration.between(s.getStartedAt(), s.getEndedAt()).getSeconds()))
                .average()
                .orElse(0.0);

        return Math.round(average);
    }

    private long countReportsByScoreRange(double minInclusive, double max, boolean includeUpperBound) {
        String jpql = includeUpperBound
                ? "SELECT COUNT(r) FROM InterviewReport r WHERE r.finalScore >= :min AND r.finalScore <= :max"
                : "SELECT COUNT(r) FROM InterviewReport r WHERE r.finalScore >= :min AND r.finalScore < :max";

        Long count = entityManager.createQuery(jpql, Long.class)
                .setParameter("min", minInclusive)
                .setParameter("max", max)
                .getSingleResult();
        return defaultLong(count);
    }

    private RoleType parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RoleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role filter: " + value);
        }
    }

    private QuestionType parseQuestionType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuestionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type filter: " + value);
        }
    }

    private DifficultyLevel parseDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DifficultyLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid difficulty filter: " + value);
        }
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(InterviewQuestion question, String normalizedSearch) {
        if (normalizedSearch.isEmpty()) {
            return true;
        }

        String haystack = String.join(
                " ",
                defaultString(question.getQuestionText(), ""),
                defaultString(question.getDomain(), ""),
                defaultString(question.getCategory(), ""),
                defaultString(question.getTags(), "")
        ).toLowerCase(Locale.ROOT);

        return haystack.contains(normalizedSearch);
    }

    private void validateQuestionPayload(QuestionUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question payload is required.");
        }
        if (request.questionText() == null || request.questionText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionText is required.");
        }
        if (request.questionText().trim().length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "questionText must be at least 10 characters.");
        }
        if (request.roleType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleType is required.");
        }
        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type is required.");
        }
        if (request.difficulty() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "difficulty is required.");
        }
        if (request.careerPathId() == null || request.careerPathId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "careerPathId is required and must be > 0.");
        }
    }

    private void applyQuestionPayload(InterviewQuestion target, QuestionUpsertRequest request, boolean createMode) {
        target.setCareerPathId(request.careerPathId());
        target.setRoleType(request.roleType());
        target.setQuestionText(request.questionText().trim());
        target.setType(request.type());
        target.setDifficulty(request.difficulty());
        target.setDomain(trimToNull(request.domain()));
        target.setCategory(trimToNull(request.category()));
        target.setExpectedPoints(trimToNull(request.expectedPoints()));
        target.setFollowUps(trimToNull(request.followUps()));
        target.setHints(trimToNull(request.hints()));
        target.setIdealAnswer(trimToNull(request.idealAnswer()));
        target.setSampleCode(trimToNull(request.sampleCode()));
        target.setTags(trimToNull(request.tags()));
        target.setMetadata(trimToNull(request.metadata()));

        boolean defaultActive = createMode || target.isActive();
        target.setActive(request.isActive() == null ? defaultActive : request.isActive());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long v) {
            return v;
        }
        if (value instanceof Integer v) {
            return v.longValue();
        }
        if (value instanceof BigInteger v) {
            return v.longValue();
        }
        if (value instanceof BigDecimal v) {
            return v.longValue();
        }
        if (value instanceof Number v) {
            return v.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double v) {
            return v;
        }
        if (value instanceof Float v) {
            return v.doubleValue();
        }
        if (value instanceof BigDecimal v) {
            return v.doubleValue();
        }
        if (value instanceof BigInteger v) {
            return v.doubleValue();
        }
        if (value instanceof Number v) {
            return v.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate v) {
            return v;
        }
        if (value instanceof java.sql.Date v) {
            return v.toLocalDate();
        }
        if (value instanceof Timestamp v) {
            return v.toLocalDateTime().toLocalDate();
        }

        String text = value.toString();
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return null;
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private long defaultLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double defaultDouble(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String defaultString(Object value, String fallback) {
        String parsed = toStringValue(value);
        return parsed == null ? fallback : parsed;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record OverviewStatsResponse(
            long totalSessions,
            long sessionsToday,
            long sessionsThisWeek,
            long completedSessions,
            long abandonedSessions,
            double completionRate,
            long totalCandidates,
            long activeCandidatesNow,
            double avgSessionScore,
            long avgSessionDuration,
            long totalQuestions,
            long activeQuestions,
            long totalAnswers,
            double avgAnswerScore,
            long totalStreaks,
            double avgStreakLength,
            long topStreakAllTime
    ) {
    }

    public record SessionsOverTimePoint(
            String date,
            long total,
            long completed,
            long abandoned
    ) {
    }

    public record ScoresOverTimePoint(
            String date,
            double avgScore,
            double avgContent,
            double avgClarity
    ) {
    }

    public record RoleBreakdownPoint(
            String role,
            long count,
            double avgScore,
            double completionRate
    ) {
    }

    public record TypeBreakdownPoint(
            String type,
            long count,
            double avgScore
    ) {
    }

    public record DifficultyBreakdownPoint(
            String difficulty,
            long count,
            double avgScore,
            double avgTime
    ) {
    }

    public record ScoreDistributionPoint(
            String bucket,
            long count
    ) {
    }

    public record QuestionIntelligencePoint(
            long questionId,
            String questionText,
            String roleType,
            String type,
            long timesAsked,
            double avgScore,
            double avgAnswerLength,
            double skipRate
    ) {
    }

    public record LeaderboardPoint(
            long userId,
            long totalSessions,
            double avgScore,
            double bestScore,
            long currentStreak,
            long totalAnswers,
            String topRole
    ) {
    }

    public record TopStreakPoint(
            long userId,
            long currentStreak,
            long longestStreak,
            long totalSessions
    ) {
    }

    public record QuestionsPageResponse(
            List<InterviewQuestionDTO> content,
            long totalElements,
            int totalPages,
            int page,
            int size,
            boolean first,
            boolean last
    ) {
    }

    public record QuestionCoveragePoint(
            String role,
            String type,
            long count,
            long active
    ) {
    }

    public record QuestionUpsertRequest(
            Long careerPathId,
            RoleType roleType,
            String questionText,
            QuestionType type,
            DifficultyLevel difficulty,
            String domain,
            String category,
            String expectedPoints,
            String followUps,
            String hints,
            String idealAnswer,
            String sampleCode,
            String tags,
            String metadata,
            @JsonAlias({"active"}) Boolean isActive
    ) {
    }
}
