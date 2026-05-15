package tn.esprit.msroadmap.DTO.response;

import tn.esprit.msroadmap.DTO.external.UserProfileDTO;
import java.time.LocalDateTime;
import java.util.List;

public record UserRoadmapResponse(
        Long id,
        Integer progressPercent,
        LocalDateTime startedAt,
        Long roadmapId,
        UserProfileDTO user, // MapStruct will ignore this due to our @Mapping
        List<StepProgressResponse> stepProgressions // Matches Entity field name exactly
) {}