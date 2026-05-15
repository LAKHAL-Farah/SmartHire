package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.DTO.request.StepResourceDto;
import tn.esprit.msroadmap.Entities.StepResource;

import java.util.List;

public interface IStepResourceService {
    List<StepResource> getResourcesByStepId(Long stepId);
    List<StepResource> getResourcesByStepIdAndType(Long stepId, String type);
    List<StepResource> searchResources(String topic, String provider, String type);
    StepResource addResourceToStep(Long stepId, StepResourceDto dto);
    void deleteResource(Long resourceId);
    void syncResourcesForStep(Long stepId);
}
