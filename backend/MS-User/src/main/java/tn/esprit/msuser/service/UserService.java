package tn.esprit.msuser.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import tn.esprit.msuser.dto.request.UserProfileRequest;
import tn.esprit.msuser.dto.request.UserRequest;
import tn.esprit.msuser.dto.response.ProfileResponse;
import tn.esprit.msuser.dto.response.UserResponse;
import tn.esprit.msuser.entity.Role;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.UserProfile;
import tn.esprit.msuser.entity.enumerated.UserStatus;
import tn.esprit.msuser.exception.DuplicateResourceException;
import tn.esprit.msuser.exception.ResourceNotFoundException;
import tn.esprit.msuser.mapper.ProfileMapper;
import tn.esprit.msuser.mapper.UserMapper;
import tn.esprit.msuser.repository.RoleRepository;
import tn.esprit.msuser.repository.UserProfileRepository;
import tn.esprit.msuser.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional

public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ProfileService profileService;
    private final UserProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.debug("Fetching all active users");
        return userRepository.findAllActive().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        log.debug("Fetching user by id: {}", id);
        User user = findUserById(id);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    public UserResponse createUser(UserProfileRequest request) {
        log.debug("Creating new user with email: {}", request.getUserRequest().getEmail());


        if (userRepository.existsByEmail(request.getUserRequest().getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getUserRequest().getEmail());
        }

        Role role = roleRepository.findByName(request.getUserRequest().getRoleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.getUserRequest().getRoleName().name()));



        User user = userMapper.toEntity(request.getUserRequest());
        user.setPasswordHash(passwordEncoder.encode(request.getUserRequest().getPassword()));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.info("User created successfully with id: {}", savedUser.getId());

        ProfileResponse profileResponse =  profileService.createProfile(user.getId(), request.getProfileRequest());
        user.setProfile(profileRepository.findByUserId(profileResponse.getUserId()).orElseThrow(() -> new RuntimeException("Profile not found for user id: " + user.getId())));
        userRepository.save(user);

        return userMapper.toResponse(savedUser);
    }

    public UserResponse updateUser(UUID id, UserRequest request) {
        log.debug("Updating user with id: {}", id);

        User user = findUserById(id);

        if (!user.getEmail().equals(request.getEmail()) &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        if (!user.getRole().getName().name().equals(request.getRoleName().name())) {
            Role newRole = roleRepository.findByName(request.getRoleName())
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + request.getRoleName().name()));
            user.setRole(newRole);
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        userMapper.updateEntity(user, request);
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with id: {}", id);

        return userMapper.toResponse(updatedUser);
    }

    public void deleteUser(UUID id) {
        log.debug("Soft deleting user with id: {}", id);

        User user = findUserById(id);
        user.setDeletedAt(LocalDateTime.now());
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);

        log.info("User soft deleted successfully with id: {}", id);
    }

    public void hardDeleteUser(UUID id) {
        log.debug("Hard deleting user with id: {}", id);

        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }

        userRepository.deleteById(id);
        log.info("User hard deleted successfully with id: {}", id);
    }

    public UserResponse updateUserStatus(UUID id, UserStatus status) {
        log.debug("Updating user status: {} for user id: {}", status, id);

        User user = findUserById(id);
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("User status updated successfully for id: {}", id);

        return userMapper.toResponse(updatedUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(UUID roleId) {
        log.debug("Fetching users by role id: {}", roleId);

        if (!roleRepository.existsById(roleId)) {
            throw new ResourceNotFoundException("Role not found with id: " + roleId);
        }

        return userRepository.findByRoleId(roleId).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    public Boolean isMfaEnabled(UUID userId){
        return findUserById(userId).getFaceRecognitionEnabled();
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}