package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.PaceSnapshotDto;
import tn.esprit.msroadmap.Entities.RoadmapPaceSnapshot;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PaceSnapshotMapper {
    @Mapping(source = "roadmap.id", target = "roadmapId")
    @Mapping(source = "timedeltaDays", target = "deltaDays")
    PaceSnapshotDto toDto(RoadmapPaceSnapshot snapshot);

    List<PaceSnapshotDto> toDtoList(List<RoadmapPaceSnapshot> snapshots);
}
