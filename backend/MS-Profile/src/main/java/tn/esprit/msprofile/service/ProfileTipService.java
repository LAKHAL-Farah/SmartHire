package tn.esprit.msprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msprofile.dto.request.ProfileTipRequest;
import tn.esprit.msprofile.dto.response.ProfileTipResponse;
import tn.esprit.msprofile.entity.CandidateCV;
import tn.esprit.msprofile.entity.GitHubProfile;
import tn.esprit.msprofile.entity.GitHubRepository;
import tn.esprit.msprofile.entity.LinkedInProfile;
import tn.esprit.msprofile.entity.ProfileTip;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;
import tn.esprit.msprofile.entity.enums.TipPriority;
import tn.esprit.msprofile.exception.ResourceNotFoundException;
import tn.esprit.msprofile.exception.ValidationException;
import tn.esprit.msprofile.repository.CandidateCVRepository;
import tn.esprit.msprofile.repository.GitHubProfileRepository;
import tn.esprit.msprofile.repository.GitHubRepositoryRepository;
import tn.esprit.msprofile.repository.LinkedInProfileRepository;
import tn.esprit.msprofile.repository.ProfileTipRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileTipService extends AbstractCrudService<ProfileTip, ProfileTipResponse> {

    private final ProfileTipRepository profileTipRepository;
    private final CandidateCVRepository candidateCVRepository;
    private final LinkedInProfileRepository linkedInProfileRepository;
    private final GitHubProfileRepository gitHubProfileRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final OpenAiService openAiService;
    private final NvidiaAiService nvidiaAiService;
    private final ObjectMapper objectMapper;


    @Override
    protected JpaRepository<ProfileTip, UUID> repository() {
        return profileTipRepository;
    }


    @Override
    protected ProfileTipResponse toResponse(ProfileTip entity) {
        return new ProfileTipResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getProfileType(),
                entity.getSourceEntityId(),
                entity.getTipText(),
                entity.getPriority(),
                entity.getIsResolved(),
                entity.getCreatedAt()
        );
    }

    @Override
    protected String resourceName() {
        return "ProfileTip";
    }

    @Transactional
    public List<ProfileTip> generateTipsForCv(UUID userId, UUID cvId) {
        CandidateCV cv = candidateCVRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CandidateCV not found with id=" + cvId + " for userId=" + userId));

        clearTipsForEntity(cvId);
        if (cv.getParsedContent() == null || cv.getParsedContent().isBlank()) {
            return List.of();
        }

        try {
            List<ProfileTip> aiTips = parseAiTips(userId, cvId, cv);
            if (!aiTips.isEmpty()) {
                List<ProfileTip> savedTips = profileTipRepository.saveAll(aiTips);
                profileTipRepository.flush();
                return savedTips;
            }
        } catch (Exception e) {
            // Fall back to the deterministic heuristic path below.
        }

        List<ProfileTip> tips = new ArrayList<>();
        if (cv.getParseStatus() == ProcessingStatus.FAILED) {
            tips.add(buildTip(userId, ProfileType.CV, cvId,
                    "CV parsing failed. Re-upload the file and ensure it is a valid PDF or DOCX.", TipPriority.HIGH));
        }

        if (cv.getAtsScore() == null) {
            tips.add(buildTip(userId, ProfileType.CV, cvId,
                    "ATS score is missing. Trigger CV parsing and scoring to get actionable optimization feedback.", TipPriority.HIGH));
        } else if (cv.getAtsScore() < 60) {
            tips.add(buildTip(userId, ProfileType.CV, cvId,
                    "ATS score is below 60. Add stronger role-specific keywords and quantify impact bullets.", TipPriority.HIGH));
        } else if (cv.getAtsScore() < 80) {
            tips.add(buildTip(userId, ProfileType.CV, cvId,
                    "ATS score can be improved. Tighten wording and align sections with target job keywords.", TipPriority.MEDIUM));
        }

        if (cv.getParsedContent() == null || cv.getParsedContent().isBlank()) {
            tips.add(buildTip(userId, ProfileType.CV, cvId,
                    "Parsed CV content is empty. Ensure your CV contains extractable text and clear section titles.", TipPriority.MEDIUM));
        }

        if (tips.isEmpty()) {
            return List.of();
        }
        List<ProfileTip> savedTips = profileTipRepository.saveAll(tips);
        profileTipRepository.flush();
        return savedTips;
    }

    @Transactional
    public List<ProfileTip> generateTipsForLinkedIn(UUID userId, UUID linkedInProfileId) {
        LinkedInProfile profile = linkedInProfileRepository.findById(linkedInProfileId)
                .filter(entity -> entity.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("LinkedInProfile not found with id=" + linkedInProfileId + " for userId=" + userId));

        if (profile.getScrapeStatus() != ProcessingStatus.COMPLETED) {
            throw new ValidationException("LinkedIn profile must be analyzed first");
        }

        clearTipsForEntity(profile.getId());

        NvidiaAiService.AiResult result = nvidiaAiService.generateLinkedInTips(
                profile.getSectionScoresJson(),
                profile.getCurrentHeadline(),
                profile.getCurrentSummary()
        );

        try {
            JsonNode node = objectMapper.readTree(result.content());
            if (!node.isArray()) {
                return List.of();
            }

            List<ProfileTip> tips = new ArrayList<>();
            for (JsonNode tipNode : node) {
                String tipText = tipNode.path("tipText").asText("").trim();
                if (tipText.isBlank()) {
                    continue;
                }

                TipPriority priority;
                try {
                    priority = TipPriority.valueOf(tipNode.path("priority").asText("MEDIUM").trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    priority = TipPriority.MEDIUM;
                }

                tips.add(buildTip(userId, ProfileType.LINKEDIN, profile.getId(), tipText, priority));
            }

            if (tips.isEmpty()) {
                return List.of();
            }
            List<ProfileTip> savedTips = profileTipRepository.saveAll(tips);
            profileTipRepository.flush();
            return savedTips;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse LinkedIn tips payload", e);
        }
    }

    @Transactional
    public List<ProfileTip> generateTipsForLinkedIn(UUID userId) {
        LinkedInProfile profile = linkedInProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("LinkedInProfile not found for userId=" + userId));
        return generateTipsForLinkedIn(userId, profile.getId());
    }

    @Transactional
    public List<ProfileTip> generateTipsForGitHub(UUID userId) {
        GitHubProfile profile = gitHubProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found for userId=" + userId));

        return generateTipsForGitHub(userId, profile.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProfileTip> generateTipsForGitHub(UUID userId, UUID githubProfileId) {
        GitHubProfile profile = gitHubProfileRepository.findById(githubProfileId)
                .filter(entity -> entity.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("GitHubProfile not found with id=" + githubProfileId + " for userId=" + userId));

        if (profile.getAuditStatus() != ProcessingStatus.COMPLETED) {
            throw new ValidationException("GitHub profile must be audited first");
        }

        clearTipsForEntity(profile.getId());

        NvidiaAiService.AiResult result = nvidiaAiService.generateGitHubTips(
                profile.getGithubUsername(),
                profile.getOverallScore() == null ? 0 : profile.getOverallScore(),
                profile.getFeedback()
        );

        try {
            JsonNode node = objectMapper.readTree(result.content());
            if (!node.isArray()) {
                return List.of();
            }

            List<ProfileTip> tips = new ArrayList<>();
            for (JsonNode tipNode : node) {
                String tipText = tipNode.path("tipText").asText("").trim();
                if (tipText.isBlank()) {
                    continue;
                }

                TipPriority priority;
                try {
                    priority = TipPriority.valueOf(tipNode.path("priority").asText("MEDIUM").trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    priority = TipPriority.MEDIUM;
                }

                tips.add(buildTip(userId, ProfileType.GITHUB, profile.getId(), tipText, priority));
            }

            return tips.isEmpty() ? List.of() : profileTipRepository.saveAll(tips);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse GitHub tips payload", e);
        }
    }

    public List<ProfileTipResponse> getTipsForUser(UUID userId) {
        return profileTipRepository.findByUserIdOrderByPriorityAscCreatedAtDesc(userId).stream()
            .sorted(tipsComparator())
                .map(this::toResponse)
                .toList();
    }

    public List<ProfileTipResponse> getTipsByType(UUID userId, ProfileType type) {
        return profileTipRepository.findByUserIdAndProfileType(userId, type).stream()
            .sorted(tipsComparator())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void markTipAsResolved(UUID tipId, UUID userId) {
        ProfileTip tip = requireEntity(tipId);
        if (!tip.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("ProfileTip not found with id=" + tipId + " for userId=" + userId);
        }
        tip.setIsResolved(Boolean.TRUE);
        profileTipRepository.save(tip);
    }

    @Transactional
    public void clearTipsForEntity(UUID sourceEntityId) {
        if (sourceEntityId != null) {
            profileTipRepository.deleteBySourceEntityId(sourceEntityId);
        }
    }

    public List<ProfileTipResponse> findByUserId(UUID userId) {
        return profileTipRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProfileTipResponse create(ProfileTipRequest request) {
        ProfileTip entity = new ProfileTip();
        apply(entity, request);
        return toResponse(profileTipRepository.save(entity));
    }

    @Transactional
    public ProfileTipResponse update(UUID id, ProfileTipRequest request) {
        ProfileTip entity = requireEntity(id);
        apply(entity, request);
        return toResponse(profileTipRepository.save(entity));
    }

    private void apply(ProfileTip entity, ProfileTipRequest request) {
        entity.setUserId(request.userId());
        entity.setProfileType(request.profileType());
        entity.setSourceEntityId(request.sourceEntityId());
        entity.setTipText(request.tipText().trim());
        entity.setPriority(request.priority());
        entity.setIsResolved(request.isResolved() != null ? request.isResolved() : Boolean.FALSE);
        entity.setCreatedAt(request.createdAt());
    }

    private ProfileTip buildTip(UUID userId, ProfileType profileType, UUID sourceEntityId, String text, TipPriority priority) {
        ProfileTip tip = new ProfileTip();
        tip.setUserId(userId);
        tip.setProfileType(profileType);
        tip.setSourceEntityId(sourceEntityId);
        tip.setTipText(text);
        tip.setPriority(priority);
        tip.setIsResolved(Boolean.FALSE);
        tip.setCreatedAt(Instant.now());
        return tip;
    }

    private List<ProfileTip> parseAiTips(UUID userId, UUID cvId, CandidateCV cv) throws Exception {
        String atsJson = cv.getAtsAnalysis() == null ? "{}" : cv.getAtsAnalysis();
        OpenAiService.AiResult result = openAiService.generateCvTips(cv.getParsedContent(), atsJson);
        JsonNode node = objectMapper.readTree(result.content());
        if (!node.isArray()) {
            return List.of();
        }

        List<ProfileTip> tips = new ArrayList<>();
        for (JsonNode tipNode : node) {
            String tipText = tipNode.path("tipText").asText("").trim();
            if (tipText.isBlank()) {
                continue;
            }
            TipPriority priority = TipPriority.valueOf(tipNode.path("priority").asText("MEDIUM").trim().toUpperCase());
            tips.add(buildTip(userId, ProfileType.CV, cvId, tipText, priority));
        }
        return tips;
    }

    private Comparator<ProfileTip> tipsComparator() {
        return Comparator.comparingInt(this::priorityRank)
                .thenComparing(ProfileTip::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int priorityRank(ProfileTip tip) {
        if (tip == null || tip.getPriority() == null) {
            return 3;
        }
        return switch (tip.getPriority()) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }
}

