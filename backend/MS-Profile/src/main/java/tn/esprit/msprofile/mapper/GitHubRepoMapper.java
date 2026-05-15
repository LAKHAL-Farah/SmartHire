package tn.esprit.msprofile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msprofile.dto.response.GitHubRepoResponse;
import tn.esprit.msprofile.entity.GitHubRepository;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring")
public interface GitHubRepoMapper {

    @Mapping(target = "pushedAt", expression = "java(formatInstant(entity.getPushedAt()))")
    GitHubRepoResponse toResponse(GitHubRepository entity);

    List<GitHubRepoResponse> toResponseList(List<GitHubRepository> entities);

    default String formatInstant(Instant value) {
        return value == null ? null : value.toString();
    }
}
