package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.entity.enums.TipPriority;

import java.time.Instant;
import java.util.UUID;

public record ProfileTipRequest(
        @NotNull UUID userId,
        @NotNull ProfileType profileType,
        UUID sourceEntityId,
        @NotBlank String tipText,
        @NotNull TipPriority priority,
        Boolean isResolved,
        Instant createdAt
) {
}

