package tn.esprit.msprofile.dto.m4;

import java.util.UUID;

public record AtsScoreResponse(
        UUID cvId,
        Integer atsScore
) {
}
