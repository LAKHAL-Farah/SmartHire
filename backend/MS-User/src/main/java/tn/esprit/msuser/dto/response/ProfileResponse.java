package tn.esprit.msuser.dto.response;



import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ProfileResponse {
    private UUID userId;
    private String firstName;
    private String lastName;
    private String headline;
    private String location;
    private String githubUrl;
    private String linkedinUrl;
    private String avatarUrl;
    private String email;

    /** Raw JSON string — parse on the client for skills / onboarding summary. */
    private String onboardingJson;
}
