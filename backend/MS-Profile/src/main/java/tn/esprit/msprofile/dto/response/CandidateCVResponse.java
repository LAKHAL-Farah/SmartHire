package tn.esprit.msprofile.dto.response;

import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record CandidateCVResponse(
        UUID id,
        UUID userId,
        String originalFileUrl,
        String originalFileName,
        FileFormat fileFormat,
        String parsedContent,
        ProcessingStatus parseStatus,
        String parseErrorMessage,
        Integer atsScore,
        Boolean isActive,
        Instant uploadedAt,
        Instant updatedAt
) {
}

