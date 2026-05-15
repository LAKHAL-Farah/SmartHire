package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.InterviewStreak;

import java.util.List;

public interface InterviewStreakService {
    InterviewStreak getOrCreateStreak(Long userId);
    InterviewStreak updateAfterSession(Long userId);
    InterviewStreak resetStreak(Long userId);
    List<InterviewStreak> getTopStreaks(int limit);
    InterviewStreak getStreakByUser(Long userId);
}
