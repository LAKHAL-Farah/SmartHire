package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.ProjectSuggestionDto;
import tn.esprit.msroadmap.Entities.ProjectSuggestion;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectSuggestionMapper {
    @Mapping(source = "step.id", target = "stepId")
    ProjectSuggestionDto toDto(ProjectSuggestion suggestion);

    List<ProjectSuggestionDto> toDtoList(List<ProjectSuggestion> suggestions);
}
