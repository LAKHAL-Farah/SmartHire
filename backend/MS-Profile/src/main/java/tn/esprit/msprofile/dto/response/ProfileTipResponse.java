package tn.esprit.msprofile.dto.response;

import tn.esprit.msprofile.entity.enums.ProfileType;
import tn.esprit.msprofile.entity.enums.TipPriority;

import java.time.Instant;
import java.util.UUID;

public record ProfileTipResponse(
        UUID id,
        UUID userId,
        ProfileType profileType,
        UUID sourceEntityId,
        String tipText,
        TipPriority priority,
        Boolean isResolved,
        Instant createdAt
) {
}

