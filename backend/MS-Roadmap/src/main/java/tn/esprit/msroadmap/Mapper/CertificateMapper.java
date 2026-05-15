package tn.esprit.msroadmap.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tn.esprit.msroadmap.DTO.response.CertificateDto;
import tn.esprit.msroadmap.Entities.MicroCertificate;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CertificateMapper {
    @Mapping(source = "milestone.id", target = "milestoneId")
    CertificateDto toDto(MicroCertificate certificate);

    List<CertificateDto> toDtoList(List<MicroCertificate> certificates);
}
