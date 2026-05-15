package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.RoadmapReplanEvent;

import java.util.List;

@Repository
public interface RoadmapReplanEventRepository extends JpaRepository<RoadmapReplanEvent, Long> {
    List<RoadmapReplanEvent> findByRoadmapIdOrderByReplannedAtDesc(Long roadmapId);
}
