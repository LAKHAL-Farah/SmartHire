package tn.esprit.msuser.dto.request;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tn.esprit.msuser.entity.enumerated.RoleName;

import java.util.UUID;

@Data
public class UserRequest {




    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Role ID is required")
    private RoleName roleName;
}