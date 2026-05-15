package tn.esprit.msuser.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.dto.request.OnboardingCompleteRequest;
import tn.esprit.msuser.dto.request.ProfileRequest;
import tn.esprit.msuser.dto.response.ProfileResponse;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.UserProfile;
import tn.esprit.msuser.exception.ResourceNotFoundException;
import tn.esprit.msuser.mapper.ProfileMapper;
import tn.esprit.msuser.repository.UserProfileRepository;
import tn.esprit.msuser.repository.UserRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProfileService {

    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProfileResponse getProfileByUserId(UUID userId) {
        log.debug("Fetching profile for user id: {}", userId);

        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user id: " + userId));

        return profileMapper.toResponse(profile);
    }

    public ProfileResponse createProfile(UUID userId, ProfileRequest request) {
        log.debug("Creating profile for user id: {}", userId);

        if (profileRepository.existsByUserId(userId)) {
            throw new IllegalStateException("Profile already exists for user id: " + userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UserProfile profile = profileMapper.toEntity(request, user);
        profile.setAvatarUrl(generateAvatarUrl(profile.getFirstName(), profile.getLastName()));
        UserProfile savedProfile = profileRepository.save(profile);

        log.info("Profile created successfully for user id: {}", userId);
        return profileMapper.toResponse(savedProfile);
    }

    public ProfileResponse updateProfile(UUID userId, ProfileRequest request) {
        log.debug("Updating profile for user id: {}", userId);

        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user id: " + userId));

        profileMapper.updateEntity(profile, request);
        UserProfile updatedProfile = profileRepository.save(profile);

        log.info("Profile updated successfully for user id: {}", userId);
        return profileMapper.toResponse(updatedProfile);
    }

    public void deleteProfile(UUID userId) {
        log.debug("Deleting profile for user id: {}", userId);

        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user id: " + userId));

        profileRepository.delete(profile);
        log.info("Profile deleted successfully for user id: {}", userId);
    }

    public ProfileResponse createOrUpdateProfile(UUID userId, ProfileRequest request) {
        if (profileRepository.existsByUserId(userId)) {
            return updateProfile(userId, request);
        } else {
            return createProfile(userId, request);
        }
    }

    public ProfileResponse completeOnboarding(UUID userId, OnboardingCompleteRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UserProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            String guessed = guessNameFromEmail(user.getEmail());
            profile = UserProfile.builder()
                    .user(user)
                    .firstName(guessed)
                    .lastName("")
                    .headline(buildOnboardingHeadline(request))
                    .avatarUrl(generateAvatarUrl(guessed, ""))
                    .build();
        } else if (profile.getHeadline() == null || profile.getHeadline().isBlank()) {
            profile.setHeadline(buildOnboardingHeadline(request));
        }

        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("situation", request.getSituation());
            snapshot.put("careerPath", request.getCareerPath());
            List<String> answers = request.getAnswers() != null ? request.getAnswers() : List.of();
            snapshot.put("answers", answers);
            snapshot.put("skillScores", request.getSkillScores() != null ? request.getSkillScores() : Map.of());
            snapshot.put("preferencesOnly", answers.isEmpty());
            if (request.getDevelopmentPlanNotes() != null) {
                snapshot.put("developmentPlanNotes", request.getDevelopmentPlanNotes());
            }
            snapshot.put("completedAt", Instant.now().toString());
            profile.setOnboardingJson(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize onboarding snapshot", e);
        }

        UserProfile saved = profileRepository.save(profile);
        log.info("Onboarding completed for user id: {}", userId);
        return profileMapper.toResponse(saved);
    }

    private static String guessNameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "Candidate";
        }
        return email.substring(0, email.indexOf('@'));
    }

    private static String buildOnboardingHeadline(OnboardingCompleteRequest request) {
        String situation = humanSituation(request.getSituation());
        String track = humanCareer(request.getCareerPath());
        return situation + " · " + track;
    }

    private static String humanSituation(String code) {
        if (code == null) return "Student / graduate";
        return switch (code) {
            case "student" -> "Computer engineering student";
            case "junior" -> "Junior engineer";
            case "switcher" -> "Career switcher into tech";
            case "experienced" -> "Experienced engineer";
            default -> "Tech candidate";
        };
    }

    private static String humanCareer(String code) {
        if (code == null) return "Exploring paths";
        return switch (code) {
            case "frontend" -> "Frontend focus";
            case "backend" -> "Backend focus";
            case "fullstack" -> "Full-stack focus";
            case "devops" -> "DevOps focus";
            case "data" -> "Data / ML focus";
            case "mobile" -> "Mobile focus";
            default -> "Software engineering";
        };
    }

    private String generateAvatarUrl(String firstName, String lastName) {
        String fn = firstName != null ? firstName : "";
        String ln = lastName != null ? lastName : "";
        return "https://ui-avatars.com/api/?name=" + fn + "+" + ln +
                "&background=0D8F81&color=fff&size=200";
    }
}
