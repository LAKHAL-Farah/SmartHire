package tn.esprit.msroadmap.Services;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.RoadmapNotification;
import tn.esprit.msroadmap.Enums.NotificationType;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.RoadmapNotificationRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapNotificationService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class RoadmapNotificationServiceImpl implements IRoadmapNotificationService {

    private final RoadmapNotificationRepository repository;
    private final RoadmapRepository roadmapRepository;

    @Override
    public List<RoadmapNotification> getNotificationsForUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<RoadmapNotification> getNotificationsForUserAndRoadmap(Long userId, Long roadmapId) {
        return repository.findByUserIdAndRoadmap_IdOrderByCreatedAtDesc(userId, roadmapId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return repository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    public RoadmapNotification markAsRead(Long notificationId) {
        RoadmapNotification n = repository.findById(notificationId).orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setRead(true);
        return repository.save(n);
    }

    @Override
    public void markAllAsRead(Long userId) {
        var list = repository.findByUserIdAndIsRead(userId, false);
        for (RoadmapNotification n : list) {
            n.setRead(true);
        }
        repository.saveAll(list);
    }

    @Override
    public RoadmapNotification createNotification(Long userId, Long roadmapId, NotificationType type, String message) {
        var roadmap = roadmapRepository.findById(roadmapId).orElse(null);
        RoadmapNotification n = new RoadmapNotification();
        n.setUserId(userId);
        n.setRoadmap(roadmap);
        n.setType(type);
        n.setMessage(message);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        return repository.save(n);
    }

    @Override
    public void deleteNotification(Long notificationId) {
        repository.deleteById(notificationId);
    }
}
