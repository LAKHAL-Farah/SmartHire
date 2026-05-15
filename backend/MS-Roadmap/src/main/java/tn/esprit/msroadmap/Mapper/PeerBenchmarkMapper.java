package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import tn.esprit.msroadmap.DTO.response.PeerBenchmarkDto;
import tn.esprit.msroadmap.Entities.PeerBenchmark;

@Mapper(componentModel = "spring")
public interface PeerBenchmarkMapper {
    PeerBenchmarkDto toDto(PeerBenchmark benchmark);
}
