package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.MilestoneDto;
import tn.esprit.msroadmap.Entities.RoadmapMilestone;
import java.util.List;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {
    @Mapping(source = "roadmap.id", target = "roadmapId")
    MilestoneDto toDto(RoadmapMilestone milestone);

    List<MilestoneDto> toDtoList(List<RoadmapMilestone> milestones);
}
