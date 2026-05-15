package tn.esprit.msprofile.dto.m4;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TailorCvRequest(
        @NotNull @NotBlank String jobOfferId
) {
}
