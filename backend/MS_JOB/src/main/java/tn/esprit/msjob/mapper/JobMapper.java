package tn.esprit.msjob.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import tn.esprit.msjob.dto.JobCreateDTO;
import tn.esprit.msjob.dto.JobDTO;
import tn.esprit.msjob.entity.Job;

import java.util.List;

@Mapper(componentModel = "spring")
public interface JobMapper {

    JobDTO toDTO(Job job);

    List<JobDTO> toDTOList(List<Job> jobs);

    /**
     * Create entity from incoming DTO.
     * postedDate is controlled by the service layer.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "postedDate", ignore = true)
    Job toEntity(JobCreateDTO dto);

    /**
     * Update an existing entity from DTO, ignoring nulls (patch-like behavior).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "postedDate", ignore = true)
    void updateEntityFromDTO(JobCreateDTO dto, @MappingTarget Job job);
}
