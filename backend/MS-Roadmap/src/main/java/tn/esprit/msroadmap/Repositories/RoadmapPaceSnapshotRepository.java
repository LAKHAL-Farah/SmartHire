package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.RoadmapPaceSnapshot;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoadmapPaceSnapshotRepository extends JpaRepository<RoadmapPaceSnapshot, Long> {
    List<RoadmapPaceSnapshot> findByRoadmapIdOrderBySnapshotDateDesc(Long roadmapId);
    RoadmapPaceSnapshot findByRoadmapIdAndSnapshotDate(Long roadmapId, LocalDate date);
    RoadmapPaceSnapshot findTopByRoadmapIdOrderBySnapshotDateDesc(Long roadmapId);
    void deleteByRoadmapId(Long roadmapId);
}
