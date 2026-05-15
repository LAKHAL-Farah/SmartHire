package tn.esprit.msprofile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msprofile.dto.LinkedInResponse;
import tn.esprit.msprofile.entity.LinkedInProfile;

@Mapper(componentModel = "spring")
public interface LinkedInMapper {

    @Mapping(target = "analysisStatus", source = "scrapeStatus")
    LinkedInResponse toResponse(LinkedInProfile entity);

}
