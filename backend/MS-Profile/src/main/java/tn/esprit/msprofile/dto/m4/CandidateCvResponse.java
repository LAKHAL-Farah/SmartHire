package tn.esprit.msprofile.dto.m4;

import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record CandidateCvResponse(
        UUID id,
        UUID userId,
        String originalFileName,
        FileFormat fileFormat,
        String parsedContent,
        String atsAnalysis,
        String completenessAnalysis,
        ProcessingStatus parseStatus,
        Integer atsScore,
        Boolean isActive,
        Instant uploadedAt,
        Instant updatedAt
) {
}
