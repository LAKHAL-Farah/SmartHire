package tn.esprit.msassessment.dto.response;

import tn.esprit.msassessment.entity.enums.SessionStatus;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
        Long id,
        String userId,
        Long categoryId,
        String categoryTitle,
        String categoryCode,
        String topicTag,
        Instant startedAt,
        Instant completedAt,
        SessionStatus status,
        /** Null for candidates when completed and results are not yet released. */
        Integer scorePercent,
        boolean scoreReleased,
        boolean isPublished,
        String notes,
        String adminFeedback,
        /** Admin only — always null in candidate-facing responses. */
        String candidateDisplayName,
        boolean integrityViolation,
        boolean forfeit,
        /**
         * AI-generated personalised advice shown immediately after submit (auto-release)
         * or after admin publish. Null while in-progress or not yet released.
         */
        List<String> advice
) {}
