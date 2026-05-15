package tn.esprit.msprofile.dto.m4;

import jakarta.validation.constraints.NotBlank;

public record CreateJobOfferRequest(
        @NotBlank String title,
        String company,
        @NotBlank String rawDescription,
        String sourceUrl
) {
}
