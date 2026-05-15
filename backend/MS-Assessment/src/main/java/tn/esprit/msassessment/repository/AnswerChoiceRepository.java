package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msassessment.entity.AnswerChoice;

@Repository
public interface AnswerChoiceRepository extends JpaRepository<AnswerChoice, Long> {
}
