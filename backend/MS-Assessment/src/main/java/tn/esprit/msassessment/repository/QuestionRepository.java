package tn.esprit.msassessment.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.msassessment.entity.Question;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = {"choices"})
    List<Question> findByCategoryIdAndActiveIsTrueOrderByIdAsc(Long categoryId);

    long countByCategory_Id(Long categoryId);

    @EntityGraph(attributePaths = {"choices", "category"})
    List<Question> findByCategory_IdOrderByIdAsc(Long categoryId);

    @Query("SELECT q FROM Question q JOIN FETCH q.category WHERE q.active = true")
    List<Question> findAllActiveWithCategoryForTopicFilter();

    @EntityGraph(attributePaths = {"choices", "category"})
    @Query("SELECT q FROM Question q WHERE q.id IN :ids")
    List<Question> findByIdInWithChoices(@Param("ids") List<Long> ids);
}
