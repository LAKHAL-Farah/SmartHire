package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.CareerPathTemplate;

import java.util.List;

@Repository
public interface CareerPathTemplateRepository extends JpaRepository<CareerPathTemplate, Long> {

	List<CareerPathTemplate> findByIsPublishedTrueOrderByTitleAsc();
}
