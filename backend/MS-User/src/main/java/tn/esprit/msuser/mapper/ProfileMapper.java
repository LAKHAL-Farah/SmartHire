package tn.esprit.msuser.mapper;


import org.springframework.stereotype.Component;
import tn.esprit.msuser.dto.request.ProfileRequest;
import tn.esprit.msuser.dto.response.ProfileResponse;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.UserProfile;

@Component
public class ProfileMapper {

    public ProfileResponse toResponse(UserProfile profile) {
        if (profile == null) return null;

        return ProfileResponse.builder()
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .headline(profile.getHeadline())
                .location(profile.getLocation())
                .githubUrl(profile.getGithubUrl())
                .linkedinUrl(profile.getLinkedinUrl())
                .avatarUrl(profile.getAvatarUrl())
                .email(profile.getUser() != null ? profile.getUser().getEmail() : null)
                .onboardingJson(profile.getOnboardingJson())
                .build();
    }

    public UserProfile toEntity(ProfileRequest request, User user) {
        if (request == null) return null;

        return UserProfile.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .headline(request.getHeadline())
                .location(request.getLocation())
                .githubUrl(request.getGithubUrl())
                .linkedinUrl(request.getLinkedinUrl())
                .avatarUrl(request.getAvatarUrl())
                .build();
    }

    public void updateEntity(UserProfile profile, ProfileRequest request) {
        if (request == null) return;

        if (request.getFirstName() != null) {
            profile.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            profile.setLastName(request.getLastName());
        }
        if (request.getHeadline() != null) {
            profile.setHeadline(request.getHeadline());
        }
        if (request.getLocation() != null) {
            profile.setLocation(request.getLocation());
        }
        if (request.getGithubUrl() != null) {
            profile.setGithubUrl(request.getGithubUrl());
        }
        if (request.getLinkedinUrl() != null) {
            profile.setLinkedinUrl(request.getLinkedinUrl());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
    }
}
