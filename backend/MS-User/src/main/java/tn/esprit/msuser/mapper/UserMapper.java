package tn.esprit.msuser.mapper;


import org.springframework.stereotype.Component;
import tn.esprit.msuser.dto.request.UserRequest;
import tn.esprit.msuser.dto.response.UserResponse;
import tn.esprit.msuser.entity.Role;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.UserProfile;
import tn.esprit.msuser.entity.enumerated.UserStatus;

import java.time.LocalDateTime;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) return null;

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .status(user.getStatus())
                .role(toRoleDto(user.getRole()))
                .profile(toProfileDto(user.getProfile()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private UserResponse.RoleDto toRoleDto(Role role) {
        if (role == null) return null;

        return UserResponse.RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .build();
    }

    private UserResponse.UserProfileDto toProfileDto(UserProfile profile) {
        if (profile == null) return null;

        return UserResponse.UserProfileDto.builder()
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .headline(profile.getHeadline())
                .location(profile.getLocation())
                .githubUrl(profile.getGithubUrl())
                .linkedinUrl(profile.getLinkedinUrl())
                .avatarUrl(profile.getAvatarUrl())
                .build();
    }

    public User toEntity(UserRequest request) {
        if (request == null) return null;

        return User.builder()
                .email(request.getEmail())
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void updateEntity(User user, UserRequest request) {
        if (request == null) return;

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
    }
}