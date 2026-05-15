package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CandidateAssignmentRegisterRequest(
        @NotNull UUID userId,
        String situation,
        String careerPath,
        String headline,
        String customSituation,
        String customCareerPath
) {}
