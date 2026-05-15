package tn.esprit.msassessment.dto.response;

/** Result of POST /admin/seed-default-bank — inserts missing seeded categories. */
public record SeedDefaultBankResponse(int added, long totalCategories) {}
