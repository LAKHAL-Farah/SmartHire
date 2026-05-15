package tn.esprit.msprofile.mapper;

import org.mapstruct.Mapper;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.entity.ProfileTip;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProfileTipMapper {
    ProfileTipResponse toResponse(ProfileTip entity);

    List<ProfileTipResponse> toResponseList(List<ProfileTip> entities);
}
