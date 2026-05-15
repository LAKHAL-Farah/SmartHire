package tn.esprit.msprofile.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msprofile.dto.m4.CvVersionResponse;
import tn.esprit.msprofile.entity.CVVersion;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CvVersionMapper {

    @Mapping(target = "cvId", source = "cv.id")
    @Mapping(target = "jobOfferId", source = "jobOffer.id")
    @Mapping(target = "diffSnapshot", expression = "java(parseDiffSnapshot(entity.getDiffContent()))")
    @Mapping(target = "completenessAnalysis", source = "completenessAnalysis")
    CvVersionResponse toResponse(CVVersion entity);

    List<CvVersionResponse> toResponseList(List<CVVersion> entities);

    default Object parseDiffSnapshot(String diffContent) {
        if (diffContent == null || diffContent.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readTree(diffContent);
        } catch (Exception ex) {
            return null;
        }
    }
}
