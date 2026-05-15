package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Confirms the session owner when forfeiting from the quiz UI (back button). */
public record ForfeitSessionRequest(@NotNull UUID userId) {}
