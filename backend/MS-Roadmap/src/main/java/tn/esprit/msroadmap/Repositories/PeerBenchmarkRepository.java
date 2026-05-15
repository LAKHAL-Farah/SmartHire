package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.PeerBenchmark;

import java.util.List;

@Repository
public interface PeerBenchmarkRepository extends JpaRepository<PeerBenchmark, Long> {
    PeerBenchmark findByCareerPathIdAndStepOrder(Long careerPathId, int stepOrder);
    List<PeerBenchmark> findByCareerPathId(Long careerPathId);
}
