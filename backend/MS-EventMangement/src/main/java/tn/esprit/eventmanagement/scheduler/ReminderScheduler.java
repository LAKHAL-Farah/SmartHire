package tn.esprit.eventmanagement.scheduler;

import tn.esprit.eventmanagement.entities.Event;
import tn.esprit.eventmanagement.repository.EventRepository;
import tn.esprit.eventmanagement.service.NotificationService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReminderScheduler {

    private final EventRepository eventRepository;
    private final NotificationService notificationService;

    public ReminderScheduler(EventRepository eventRepository,
                             NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 60000) // toutes les minutes
    public void checkUpcomingEvents() {
        System.out.println("🔁 Scheduler running: " + LocalDateTime.now());
        List<Event> events = eventRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Event event : events) {
            LocalDateTime start = event.getStartDate();
            if (start == null) continue;

            long minutes = Duration.between(now, start).toMinutes();

            // Entre 59 et 61 minutes pour éviter les problèmes de timing exact
            if (minutes >= 59 && minutes <= 61) {
                notificationService.sendEventReminder(
                        event.getId(),
                        event.getTitle()
                );
                System.out.println("✅ Reminder sent for: " + event.getTitle());
            }
        }
    }
}