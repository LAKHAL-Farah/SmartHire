package tn.esprit.msassessment.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryAdminRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description
) {}
