package tn.esprit.msroadmap.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.msroadmap.Entities.StepResource;
import tn.esprit.msroadmap.Enums.ResourceProvider;
import tn.esprit.msroadmap.Enums.ResourceType;

import java.util.List;

@Repository
public interface StepResourceRepository extends JpaRepository<StepResource, Long> {
    List<StepResource> findByStepId(Long stepId);
    List<StepResource> findByStepIdAndType(Long stepId, ResourceType type);
    List<StepResource> findByStepIdAndProvider(Long stepId, ResourceProvider provider);
    boolean existsByStepIdAndExternalId(Long stepId, String externalId);
    void deleteByStepId(Long stepId);

    @Query("select r from StepResource r where (:type is null or r.type = :type) and (:provider is null or r.provider = :provider)")
    List<StepResource> searchByTypeAndProvider(ResourceType type, ResourceProvider provider);
}
