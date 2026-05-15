package tn.esprit.msuser.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Payload when a candidate finishes the post sign-up onboarding flow (situation, career, quick quiz).
 */
@Data
public class OnboardingCompleteRequest {

    @NotBlank
    @Size(max = 64)
    private String situation;

    @NotBlank
    @Size(max = 64)
    private String careerPath;

    /**
     * Optional MCQ selections from a skill quiz. Preferences-only onboarding sends an empty list.
     */
    @Size(max = 20)
    private List<String> answers;

    /**
     * Normalized 0–100 skill scores (e.g. Frontend, Backend) computed on the client or server.
     */
    private Map<String, Integer> skillScores;

    /** Optional development plan bullets for the profile. */
    @Size(max = 2000)
    private String developmentPlanNotes;
}
