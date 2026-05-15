package tn.esprit.msjob.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msjob.dto.JobApplicationDTO;
import tn.esprit.msjob.entity.JobApplication;

import java.util.List;

@Mapper(componentModel = "spring")
public interface JobApplicationMapper {

    @Mapping(target = "jobId", source = "job.id")
    JobApplicationDTO toDTO(JobApplication entity);

    List<JobApplicationDTO> toDTOList(List<JobApplication> entities);
}

