package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.Entities.RoadmapNotification;
import tn.esprit.msroadmap.Enums.NotificationType;

import java.util.List;

public interface IRoadmapNotificationService {
    List<RoadmapNotification> getNotificationsForUser(Long userId);
    List<RoadmapNotification> getNotificationsForUserAndRoadmap(Long userId, Long roadmapId);
    long getUnreadCount(Long userId);
    RoadmapNotification markAsRead(Long notificationId);
    void markAllAsRead(Long userId);
    RoadmapNotification createNotification(Long userId, Long roadmapId, NotificationType type, String message);
    void deleteNotification(Long notificationId);
}
