package tn.esprit.msprofile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msprofile.dto.m4.CandidateCvResponse;
import tn.esprit.msprofile.entity.CandidateCV;

@Mapper(componentModel = "spring")
public interface CandidateCvMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "completenessAnalysis", source = "completenessAnalysis")
    CandidateCvResponse toResponse(CandidateCV entity);
}
