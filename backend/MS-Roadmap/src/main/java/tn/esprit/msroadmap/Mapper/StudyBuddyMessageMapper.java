package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.StudyBuddyMessageDto;
import tn.esprit.msroadmap.Entities.StudyBuddyMessage;
import java.util.List;

@Mapper(componentModel = "spring")
public interface StudyBuddyMessageMapper {
    @Mapping(source = "step.id", target = "stepId")
    StudyBuddyMessageDto toDto(StudyBuddyMessage message);

    List<StudyBuddyMessageDto> toDtoList(List<StudyBuddyMessage> messages);
}
