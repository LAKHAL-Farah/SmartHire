package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.StepResourceDto;
import tn.esprit.msroadmap.Entities.StepResource;
import java.util.List;

@Mapper(componentModel = "spring")
public interface StepResourceMapper {
    @Mapping(source = "step.id", target = "stepId")
    StepResourceDto toDto(StepResource resource);

    List<StepResourceDto> toDtoList(List<StepResource> resources);
}
