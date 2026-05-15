package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.StepDetailResponse;
import tn.esprit.msroadmap.DTO.response.StepResponse;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import java.util.List;

@Mapper(componentModel = "spring")
public interface RoadmapStepMapper {
    StepResponse toResponse(RoadmapStep step);

    StepDetailResponse toDetailResponse(RoadmapStep step);

    List<StepResponse> toResponseList(List<RoadmapStep> steps);
}
