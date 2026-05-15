package tn.esprit.msuser.dto.request;



import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tn.esprit.msuser.entity.enumerated.AuthProvider;

@Data
public class OAuthConnectionRequest {
    @NotNull(message = "Provider is required")
    private AuthProvider provider;

    @NotNull(message = "Provider user ID is required")
    private String providerUserId;
}