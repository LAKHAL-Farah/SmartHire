package tn.esprit.msuser.dto.response;


import lombok.Builder;
import lombok.Data;
import tn.esprit.msuser.entity.enumerated.AuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OAuthConnectionResponse {
    private UUID id;
    private AuthProvider provider;
    private String providerUserId;
    private LocalDateTime connectedAt;
    private UUID userId;
}