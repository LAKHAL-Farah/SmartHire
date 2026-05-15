package tn.esprit.msuser.dto.response;



import lombok.Builder;
import lombok.Data;
import tn.esprit.msuser.entity.enumerated.RoleName;
import tn.esprit.msuser.entity.enumerated.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private UserStatus status;
    private RoleDto role;
    private UserProfileDto profile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class RoleDto {
        private UUID id;
        private RoleName name;
    }

    @Data
    @Builder
    public static class UserProfileDto {
        private String firstName;
        private String lastName;
        private String headline;
        private String location;
        private String githubUrl;
        private String linkedinUrl;
        private String avatarUrl;
    }
}