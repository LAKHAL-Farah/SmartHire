package tn.esprit.msuser.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msuser.dto.request.OAuthConnectionRequest;
import tn.esprit.msuser.dto.response.OAuthConnectionResponse;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.enumerated.AuthProvider;
import tn.esprit.msuser.service.OAuthConnectionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oauth-connections")
@RequiredArgsConstructor
public class OAuthConnectionController {

    private final OAuthConnectionService connectionService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OAuthConnectionResponse>> getConnectionsByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(connectionService.getConnectionsByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OAuthConnectionResponse> getConnectionById(@PathVariable UUID id) {
        return ResponseEntity.ok(connectionService.getConnectionById(id));
    }

    @GetMapping("/provider")
    public ResponseEntity<OAuthConnectionResponse> getConnectionByProviderAndProviderUserId(
            @RequestParam AuthProvider provider,
            @RequestParam String providerUserId) {
        return ResponseEntity.ok(
                connectionService.getConnectionByProviderAndProviderUserId(provider, providerUserId));
    }

    @GetMapping("/user")
    public ResponseEntity<User> getUserByOAuthConnection(
            @RequestParam AuthProvider provider,
            @RequestParam String providerUserId) {
        return ResponseEntity.ok(
                connectionService.getUserByOAuthConnection(provider, providerUserId));
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<OAuthConnectionResponse> createConnection(
            @PathVariable UUID userId,
            @Valid @RequestBody OAuthConnectionRequest request) {
        OAuthConnectionResponse createdConnection = connectionService.createConnection(userId, request);
        return new ResponseEntity<>(createdConnection, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable UUID id) {
        connectionService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteAllUserConnections(@PathVariable UUID userId) {
        connectionService.deleteAllUserConnections(userId);
        return ResponseEntity.noContent().build();
    }
}