package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.RoadmapNotification;

import java.util.List;

@Repository
public interface RoadmapNotificationRepository extends JpaRepository<RoadmapNotification, Long> {
    List<RoadmapNotification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<RoadmapNotification> findByUserIdAndRoadmap_IdOrderByCreatedAtDesc(Long userId, Long roadmapId);
    List<RoadmapNotification> findByUserIdAndIsRead(Long userId, boolean isRead);
    long countByUserIdAndIsRead(Long userId, boolean isRead);
}
