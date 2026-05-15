package tn.esprit.msroadmap.Services;

import tn.esprit.msroadmap.Entities.Roadmap;

import java.time.LocalDate;

public final class RoadmapProgressCalculator {

    private RoadmapProgressCalculator() {
    }

    public static void updateStreakDays(Roadmap roadmap, LocalDate activityDate) {
        LocalDate yesterday = activityDate.minusDays(1);

        if (roadmap.getLastActivityDate() == null || roadmap.getLastActivityDate().isBefore(yesterday)) {
            roadmap.setStreakDays(1);
        } else if (roadmap.getLastActivityDate().equals(yesterday)) {
            roadmap.setStreakDays(roadmap.getStreakDays() + 1);
        }

        if (roadmap.getStreakDays() > roadmap.getLongestStreak()) {
            roadmap.setLongestStreak(roadmap.getStreakDays());
        }

        roadmap.setLastActivityDate(activityDate);
    }
}
