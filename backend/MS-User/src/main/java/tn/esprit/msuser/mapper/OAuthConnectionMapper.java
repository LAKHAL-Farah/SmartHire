package tn.esprit.msuser.mapper;


import org.springframework.stereotype.Component;
import tn.esprit.msuser.dto.request.OAuthConnectionRequest;
import tn.esprit.msuser.dto.response.OAuthConnectionResponse;
import tn.esprit.msuser.entity.OAuthConnection;
import tn.esprit.msuser.entity.User;

import java.time.LocalDateTime;

@Component
public class OAuthConnectionMapper {

    public OAuthConnectionResponse toResponse(OAuthConnection connection) {
        if (connection == null) return null;

        return OAuthConnectionResponse.builder()
                .id(connection.getId())
                .provider(connection.getProvider())
                .providerUserId(connection.getProviderUserId())
                .connectedAt(connection.getConnectedAt())
                .userId(connection.getUser() != null ? connection.getUser().getId() : null)
                .build();
    }

    public OAuthConnection toEntity(OAuthConnectionRequest request, User user) {
        if (request == null) return null;

        return OAuthConnection.builder()
                .user(user)
                .provider(request.getProvider())
                .providerUserId(request.getProviderUserId())
                .connectedAt(LocalDateTime.now())
                .build();
    }
}