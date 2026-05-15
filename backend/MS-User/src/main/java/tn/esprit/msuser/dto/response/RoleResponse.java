package tn.esprit.msuser.dto.response;



import lombok.Builder;
import lombok.Data;
import tn.esprit.msuser.entity.enumerated.RoleName;

import java.util.UUID;

@Data
@Builder
public class RoleResponse {
    private UUID id;
    private RoleName name;
    private Long userCount;
}