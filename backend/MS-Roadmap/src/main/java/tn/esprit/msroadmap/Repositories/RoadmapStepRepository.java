package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import tn.esprit.msroadmap.Enums.StepStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapStepRepository extends JpaRepository<RoadmapStep, Long> {
	List<RoadmapStep> findByRoadmapIdOrderByStepOrderAsc(Long roadmapId);
	List<RoadmapStep> findByRoadmapIdAndStatus(Long roadmapId, StepStatus status);
	RoadmapStep findByRoadmapIdAndStepOrder(Long roadmapId, Integer stepOrder);
	long countByRoadmapIdAndStatus(Long roadmapId, StepStatus status);
	RoadmapStep findFirstByRoadmapIdAndStatusOrderByStepOrderAsc(Long roadmapId, StepStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from RoadmapStep s where s.id = :id")
	Optional<RoadmapStep> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from RoadmapStep s where s.roadmap.id = :roadmapId and s.stepOrder = :stepOrder")
	Optional<RoadmapStep> findByRoadmapIdAndStepOrderForUpdate(@Param("roadmapId") Long roadmapId, @Param("stepOrder") Integer stepOrder);
}
