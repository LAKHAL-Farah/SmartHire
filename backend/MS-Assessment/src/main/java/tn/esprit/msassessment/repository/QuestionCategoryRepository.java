package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msassessment.entity.QuestionCategory;

import java.util.Optional;

@Repository
public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, Long> {

    Optional<QuestionCategory> findByCode(String code);
}
