package tn.esprit.msassessment.dto.response;

/** Exposed to the candidate — never includes whether the choice is correct. */
public record ChoiceView(
        Long id,
        String label
) {}
