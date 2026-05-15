package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.NotificationDto;
import tn.esprit.msroadmap.Entities.RoadmapNotification;
import java.util.List;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(source = "roadmap.id", target = "roadmapId")
    NotificationDto toDto(RoadmapNotification notification);

    List<NotificationDto> toDtoList(List<RoadmapNotification> notifications);
}
