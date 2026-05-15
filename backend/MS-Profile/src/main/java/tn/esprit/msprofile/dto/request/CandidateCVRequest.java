package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.msprofile.entity.enums.FileFormat;
import tn.esprit.msprofile.entity.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record CandidateCVRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 512) String originalFileUrl,
        @NotBlank @Size(max = 255) String originalFileName,
        @NotNull FileFormat fileFormat,
        String parsedContent,
        ProcessingStatus parseStatus,
        @Size(max = 500) String parseErrorMessage,
        @Min(0) @Max(100) Integer atsScore,
        Boolean isActive,
        Instant uploadedAt,
        Instant updatedAt
) {
}

