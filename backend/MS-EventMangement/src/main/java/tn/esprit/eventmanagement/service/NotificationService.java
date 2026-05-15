package tn.esprit.eventmanagement.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendNotification(String message) {
        messagingTemplate.convertAndSend("/topic/notifications", message);
    }

    public void sendEventReminder(Long eventId, String title) {
        String msg = "🔔 Reminder: \"" + title + "\" starts in 1 hour!";
        messagingTemplate.convertAndSend("/topic/notifications", msg);
        System.out.println("📤 WebSocket message sent: " + msg);
    }
}