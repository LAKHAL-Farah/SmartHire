package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.msprofile.entity.enums.CVVersionType;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CVVersionRequest(
        @NotNull UUID cvId,
        UUID jobOfferId,
        @NotNull CVVersionType versionType,
        @NotBlank String tailoredContent,
        @Min(0) @Max(100) Integer atsScore,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal keywordMatchRate,
        String diffContent,
        Boolean generatedByAI,
        ProcessingStatus processingStatus,
        @Size(max = 512) String exportedFileUrl,
        Instant generatedAt
) {
}

