package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.ReplanEventDto;
import tn.esprit.msroadmap.Entities.RoadmapReplanEvent;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ReplanEventMapper {
    @Mapping(source = "roadmap.id", target = "roadmapId")
    ReplanEventDto toDto(RoadmapReplanEvent event);

    List<ReplanEventDto> toDtoList(List<RoadmapReplanEvent> events);
}
