package tn.esprit.msassessment.dto.request;

import jakarta.validation.constraints.Size;

/** Optional client hint (e.g. {@code visibility_hidden}). */
public record IntegrityViolationRequest(@Size(max = 64) String reason) {}
