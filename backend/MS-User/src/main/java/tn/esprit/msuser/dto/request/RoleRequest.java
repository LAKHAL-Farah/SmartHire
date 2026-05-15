package tn.esprit.msuser.dto.request;



import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tn.esprit.msuser.entity.enumerated.RoleName;

@Data
public class RoleRequest {
    @NotNull(message = "Role name is required")
    private RoleName name;
}