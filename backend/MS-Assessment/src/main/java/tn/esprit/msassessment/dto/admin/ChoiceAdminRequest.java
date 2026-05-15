package tn.esprit.msassessment.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChoiceAdminRequest(
        @NotBlank @Size(max = 4000) String label,
        boolean correct,
        @NotNull Integer sortOrder
) {}
