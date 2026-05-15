package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.UserStepBenchmarkDto;
import tn.esprit.msroadmap.Entities.UserStepBenchmark;

@Mapper(componentModel = "spring", uses = {PeerBenchmarkMapper.class})
public interface UserStepBenchmarkMapper {
    @Mapping(source = "roadmapStep.id", target = "stepId")
    UserStepBenchmarkDto toDto(UserStepBenchmark benchmark);
}
