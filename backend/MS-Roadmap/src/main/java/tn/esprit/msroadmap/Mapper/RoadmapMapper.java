package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.Entities.*;
import tn.esprit.msroadmap.DTO.request.*;
import tn.esprit.msroadmap.DTO.response.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoadmapMapper {

    // --- ROADMAP MAPPINGS ---
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "steps", ignore = true)
    Roadmap toEntity(RoadmapRequest request);

    @Mapping(target = "careerPath", ignore = true)
    RoadmapResponse toResponse(Roadmap entity);

    // --- STEP MAPPINGS ---
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roadmap", ignore = true)
    @Mapping(source = "estimatedDays", target = "estimatedDays")
    RoadmapStep toStepEntity(StepRequest request);

    @Mapping(source = "estimatedDays", target = "estimatedDays")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    StepResponse toStepResponse(RoadmapStep entity);

    // --- USER PROGRESS MAPPINGS ---

    /**
     * We ignore "user" because it exists in the DTO but not the Entity.
     * "stepProgressions" matches in both, so MapStruct maps it automatically.
     */
    @Mapping(target = "user", ignore = true)
    UserRoadmapResponse toUserRoadmapResponse(UserRoadmap entity);

    StepProgressResponse toProgressResponse(UserStepProgress entity);

    // --- COLLECTION MAPPINGS ---
    List<StepResponse> toStepResponseList(List<RoadmapStep> steps);

    List<StepProgressResponse> toProgressResponseList(List<UserStepProgress> stepProgressions);
}