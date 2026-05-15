package tn.esprit.msprofile.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AlignLinkedInRequest(
        @NotNull UUID jobOfferId
) {
}
