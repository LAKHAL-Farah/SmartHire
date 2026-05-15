package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Enums.RoadmapStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
	List<Roadmap> findByUserId(Long userId);
	List<Roadmap> findByUserIdAndStatus(Long userId, RoadmapStatus status);
	List<Roadmap> findByCareerPathId(Long careerPathId);
	Roadmap findByShareToken(String token);
	long countByUserIdAndStatus(Long userId, RoadmapStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Roadmap r where r.id = :id")
	Optional<Roadmap> findByIdForUpdate(@Param("id") Long id);
}
