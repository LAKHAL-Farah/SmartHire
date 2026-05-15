package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msinterview.entity.InterviewQuestion;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;

import java.util.List;

@Repository
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findByIsActiveTrue();

    List<InterviewQuestion> findByRoleTypeAndTypeAndDifficulty(RoleType roleType, QuestionType type, DifficultyLevel difficulty);

    List<InterviewQuestion> findByRoleTypeAndType(RoleType roleType, QuestionType type);

    List<InterviewQuestion> findByCareerPathIdAndIsActiveTrue(Long careerPathId);

    List<InterviewQuestion> findByTypeAndIsActiveTrue(QuestionType type);

    List<InterviewQuestion> findByRoleTypeInAndIsActiveTrue(List<RoleType> roles);

    long countByRoleTypeAndType(RoleType roleType, QuestionType type);

    List<InterviewQuestion> findByDomainAndIsActiveTrue(String domain);

    @Query("SELECT q FROM InterviewQuestion q WHERE q.id NOT IN :ids AND q.isActive = true AND q.roleType IN :roles AND q.type = :type AND q.difficulty = :diff")
    List<InterviewQuestion> findExcludingIds(
            @Param("ids") List<Long> ids,
            @Param("roles") List<RoleType> roles,
            @Param("type") QuestionType type,
            @Param("diff") DifficultyLevel difficulty);
}

