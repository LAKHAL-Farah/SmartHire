package tn.esprit.msuser.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.dto.request.OAuthConnectionRequest;
import tn.esprit.msuser.dto.response.OAuthConnectionResponse;
import tn.esprit.msuser.entity.OAuthConnection;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.enumerated.AuthProvider;
import tn.esprit.msuser.exception.DuplicateResourceException;
import tn.esprit.msuser.exception.ResourceNotFoundException;
import tn.esprit.msuser.mapper.OAuthConnectionMapper;
import tn.esprit.msuser.repository.OAuthConnectionRepository;
import tn.esprit.msuser.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OAuthConnectionService {

    private final OAuthConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final OAuthConnectionMapper connectionMapper;

    @Transactional(readOnly = true)
    public List<OAuthConnectionResponse> getConnectionsByUserId(UUID userId) {
        log.debug("Fetching OAuth connections for user id: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        return connectionRepository.findByUserId(userId).stream()
                .map(connectionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OAuthConnectionResponse getConnectionById(UUID id) {
        log.debug("Fetching OAuth connection by id: {}", id);

        OAuthConnection connection = connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OAuth connection not found with id: " + id));

        return connectionMapper.toResponse(connection);
    }

    @Transactional(readOnly = true)
    public OAuthConnectionResponse getConnectionByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        log.debug("Fetching OAuth connection by provider: {} and providerUserId: {}", provider, providerUserId);

        OAuthConnection connection = connectionRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OAuth connection not found for provider: " + provider + " and providerUserId: " + providerUserId));

        return connectionMapper.toResponse(connection);
    }

    public OAuthConnectionResponse createConnection(UUID userId, OAuthConnectionRequest request) {
        log.debug("Creating OAuth connection for user id: {}", userId);

        if (connectionRepository.existsByProviderAndProviderUserId(request.getProvider(), request.getProviderUserId())) {
            throw new DuplicateResourceException(
                    "OAuth connection already exists for provider: " + request.getProvider() +
                            " and providerUserId: " + request.getProviderUserId());
        }

        if (connectionRepository.findByUserIdAndProvider(userId, request.getProvider()).isPresent()) {
            throw new DuplicateResourceException(
                    "User already has a connection with provider: " + request.getProvider());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        OAuthConnection connection = connectionMapper.toEntity(request, user);
        OAuthConnection savedConnection = connectionRepository.save(connection);

        log.info("OAuth connection created successfully with id: {}", savedConnection.getId());
        return connectionMapper.toResponse(savedConnection);
    }

    public void deleteConnection(UUID id) {
        log.debug("Deleting OAuth connection with id: {}", id);

        OAuthConnection connection = connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OAuth connection not found with id: " + id));

        connectionRepository.delete(connection);
        log.info("OAuth connection deleted successfully with id: {}", id);
    }

    public void deleteAllUserConnections(UUID userId) {
        log.debug("Deleting all OAuth connections for user id: {}", userId);

        List<OAuthConnection> connections = connectionRepository.findByUserId(userId);
        connectionRepository.deleteAll(connections);

        log.info("All OAuth connections deleted for user id: {}", userId);
    }

    @Transactional(readOnly = true)
    public User getUserByOAuthConnection(AuthProvider provider, String providerUserId) {
        log.debug("Finding user by OAuth connection - provider: {}, providerUserId: {}", provider, providerUserId);

        OAuthConnection connection = connectionRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No user found for provider: " + provider + " and providerUserId: " + providerUserId));

        return connection.getUser();
    }
}