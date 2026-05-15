package tn.esprit.msprofile.dto.m4;

import tn.esprit.msprofile.entity.enums.CVVersionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CvVersionResponse(
        UUID id,
        UUID cvId,
        UUID jobOfferId,
        CVVersionType versionType,
        String tailoredContent,
        Integer atsScore,
        BigDecimal keywordMatchRate,
        String atsAnalysis,
        Object diffSnapshot,
        String completenessAnalysis,
        String exportedFileUrl,
        Instant generatedAt
) {
}
