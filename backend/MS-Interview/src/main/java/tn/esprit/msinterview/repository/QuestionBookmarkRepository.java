package tn.esprit.msinterview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.esprit.msinterview.entity.QuestionBookmark;

import java.util.List;
import java.util.Optional;

public interface QuestionBookmarkRepository extends JpaRepository<QuestionBookmark, Long> {
    List<QuestionBookmark> findByUserIdOrderBySavedAtDesc(Long userId);
    
    List<QuestionBookmark> findByUserIdAndTagLabel(Long userId, String tagLabel);
    
    Optional<QuestionBookmark> findByUserIdAndQuestionId(Long userId, Long questionId);
    
    boolean existsByUserIdAndQuestionId(Long userId, Long questionId);
    
    void deleteByUserIdAndQuestionId(Long userId, Long questionId);
    
    @Query("SELECT DISTINCT b.tagLabel FROM QuestionBookmark b WHERE b.userId=:uid AND b.tagLabel IS NOT NULL")
    List<String> findTagsByUserId(@Param("uid") Long userId);
}
