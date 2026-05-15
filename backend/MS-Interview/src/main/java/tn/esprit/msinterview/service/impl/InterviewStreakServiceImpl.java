package tn.esprit.msinterview.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msinterview.entity.InterviewSession;
import tn.esprit.msinterview.entity.InterviewStreak;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.repository.InterviewSessionRepository;
import tn.esprit.msinterview.repository.InterviewStreakRepository;
import tn.esprit.msinterview.service.InterviewStreakService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewStreakServiceImpl implements InterviewStreakService {

    private final InterviewStreakRepository interviewStreakRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    @Override
    @Transactional
    public InterviewStreak getOrCreateStreak(Long userId) {
        return interviewStreakRepository.findByUserId(userId)
                .orElseGet(() -> {
                    InterviewStreak newStreak = InterviewStreak.builder()
                            .userId(userId)
                            .currentStreak(0)
                            .longestStreak(0)
                            .totalSessionsCompleted(0)
                            .lastSessionDate(null)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return interviewStreakRepository.save(newStreak);
                });
    }

    @Override
    @Transactional
    public InterviewStreak updateAfterSession(Long userId) {
        InterviewStreak streak = getOrCreateStreak(userId);
        
        LocalDate today = LocalDate.now();
        LocalDate lastSessionDate = streak.getLastSessionDate();
        
        // Check if this is a new streak day
        if (lastSessionDate == null) {
            // First session
            streak.setCurrentStreak(1);
        } else if (lastSessionDate.equals(today)) {
            // Already completed a session today, don't increment
            streak.setTotalSessionsCompleted(streak.getTotalSessionsCompleted() + 1);
            streak.setUpdatedAt(LocalDateTime.now());
            return interviewStreakRepository.save(streak);
        } else if (lastSessionDate.plusDays(1).equals(today)) {
            // Continue streak
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        } else {
            // Streak broken, restart at 1
            streak.setCurrentStreak(1);
        }
        
        // Update longest streak if necessary
        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }
        
        streak.setLastSessionDate(today);
        streak.setTotalSessionsCompleted(streak.getTotalSessionsCompleted() + 1);
        streak.setUpdatedAt(LocalDateTime.now());
        
        return interviewStreakRepository.save(streak);
    }

    @Override
    @Transactional
    public InterviewStreak resetStreak(Long userId) {
        InterviewStreak streak = getOrCreateStreak(userId);
        streak.setCurrentStreak(0);
        streak.setUpdatedAt(LocalDateTime.now());
        
        log.info("Streak reset for userId: {}", userId);
        return interviewStreakRepository.save(streak);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewStreak> getTopStreaks(int limit) {
        return interviewStreakRepository.findTopStreaks(PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public InterviewStreak getStreakByUser(Long userId) {
        InterviewStreak streak = interviewStreakRepository.findByUserId(userId)
                .orElseGet(() -> {
                    InterviewStreak newStreak = InterviewStreak.builder()
                            .userId(userId)
                            .currentStreak(0)
                            .longestStreak(0)
                            .totalSessionsCompleted(0)
                            .lastSessionDate(null)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    log.info("Creating new streak for userId: {}", userId);
                    return interviewStreakRepository.save(newStreak);
                });

        // Keep streak values consistent with historical completed sessions.
        return synchronizeFromSessionHistory(streak);
    }

    private InterviewStreak synchronizeFromSessionHistory(InterviewStreak streak) {
        List<InterviewSession> completedSessions = interviewSessionRepository.findByUserIdAndStatus(
            streak.getUserId(),
            SessionStatus.COMPLETED
        );

        if (completedSessions.isEmpty()) {
            completedSessions = interviewSessionRepository.findAllByUserIdAndStatusFromSessionTable(
                streak.getUserId(),
                SessionStatus.COMPLETED.name()
            );
        }

        if (completedSessions.isEmpty()) {
            boolean changed = false;

            if (streak.getCurrentStreak() != 0) {
                streak.setCurrentStreak(0);
                changed = true;
            }
            if (streak.getLongestStreak() != 0) {
                streak.setLongestStreak(0);
                changed = true;
            }
            if (streak.getTotalSessionsCompleted() != 0) {
                streak.setTotalSessionsCompleted(0);
                changed = true;
            }
            if (streak.getLastSessionDate() != null) {
                streak.setLastSessionDate(null);
                changed = true;
            }

            if (changed) {
                streak.setUpdatedAt(LocalDateTime.now());
                return interviewStreakRepository.save(streak);
            }

            return streak;
        }

        int totalSessions = completedSessions.size();

        Set<LocalDate> completionDays = new TreeSet<>();
        for (InterviewSession session : completedSessions) {
            LocalDateTime marker = session.getEndedAt() != null ? session.getEndedAt() : session.getStartedAt();
            if (marker != null) {
                completionDays.add(marker.toLocalDate());
            }
        }

        if (completionDays.isEmpty()) {
            return streak;
        }

        int run = 0;
        int longest = 0;
        LocalDate previous = null;
        for (LocalDate day : completionDays) {
            if (previous == null || previous.plusDays(1).equals(day)) {
                run += 1;
            } else {
                run = 1;
            }

            if (run > longest) {
                longest = run;
            }

            previous = day;
        }

        LocalDate lastSessionDate = ((TreeSet<LocalDate>) completionDays).last();

        boolean changed = false;
        if (!Integer.valueOf(run).equals(streak.getCurrentStreak())) {
            streak.setCurrentStreak(run);
            changed = true;
        }
        if (!Integer.valueOf(longest).equals(streak.getLongestStreak())) {
            streak.setLongestStreak(longest);
            changed = true;
        }
        if (!Integer.valueOf(totalSessions).equals(streak.getTotalSessionsCompleted())) {
            streak.setTotalSessionsCompleted(totalSessions);
            changed = true;
        }
        if (!lastSessionDate.equals(streak.getLastSessionDate())) {
            streak.setLastSessionDate(lastSessionDate);
            changed = true;
        }

        if (changed) {
            streak.setUpdatedAt(LocalDateTime.now());
            return interviewStreakRepository.save(streak);
        }

        return streak;
    }
}
