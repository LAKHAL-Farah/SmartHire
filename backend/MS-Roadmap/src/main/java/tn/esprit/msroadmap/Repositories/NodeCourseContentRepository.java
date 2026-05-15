package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.NodeCourseContent;

import java.util.List;
import java.util.Optional;

@Repository
public interface NodeCourseContentRepository extends JpaRepository<NodeCourseContent, Long> {
    Optional<NodeCourseContent> findFirstByNodeIdAndUserIdOrderByGeneratedAtDesc(Long nodeId, Long userId);
    List<NodeCourseContent> findTop12ByNodeIdAndUserIdOrderByGeneratedAtDesc(Long nodeId, Long userId);
}
