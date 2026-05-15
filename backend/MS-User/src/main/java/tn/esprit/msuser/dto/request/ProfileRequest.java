package tn.esprit.msuser.dto.request;



import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileRequest {
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Size(max = 200, message = "Headline must not exceed 200 characters")
    private String headline;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    private String githubUrl;
    private String linkedinUrl;
    private String avatarUrl;
}