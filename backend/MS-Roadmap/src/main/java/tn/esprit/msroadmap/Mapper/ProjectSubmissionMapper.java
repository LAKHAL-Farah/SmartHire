package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.ProjectSubmissionDto;
import tn.esprit.msroadmap.Entities.ProjectSubmission;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProjectSubmissionMapper {
    @Mapping(source = "projectSuggestion.id", target = "projectSuggestionId")
    ProjectSubmissionDto toDto(ProjectSubmission submission);

    List<ProjectSubmissionDto> toDtoList(List<ProjectSubmission> submissions);

    default List<String> map(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\\\n|,"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
