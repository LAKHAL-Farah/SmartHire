package tn.esprit.msprofile.mapper;

import org.mapstruct.Mapper;
import tn.esprit.msprofile.dto.m4.JobOfferResponse;
import tn.esprit.msprofile.entity.JobOffer;

import java.util.List;

@Mapper(componentModel = "spring")
public interface JobOfferMapper {

    JobOfferResponse toResponse(JobOffer entity);

    List<JobOfferResponse> toResponseList(List<JobOffer> entities);
}
