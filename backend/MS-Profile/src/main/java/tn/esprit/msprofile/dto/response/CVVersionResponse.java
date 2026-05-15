package tn.esprit.msprofile.dto.response;

import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CVVersionResponse(
        UUID id,
        UUID cvId,
        UUID jobOfferId,
        CVVersionType versionType,
        String tailoredContent,
        Integer atsScore,
        BigDecimal keywordMatchRate,
        String diffContent,
        Boolean generatedByAI,
        ProcessingStatus processingStatus,
        String exportedFileUrl,
        Instant generatedAt
) {
}

