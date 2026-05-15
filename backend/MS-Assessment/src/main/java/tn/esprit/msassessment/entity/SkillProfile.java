package tn.esprit.msassessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Aggregated skill snapshot for a candidate, derived from published assessment attempts.
 * One row per user; updated whenever a completed session becomes visible to the candidate.
 */
@Entity
@Table(name = "msa_skill_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private String userId;

    /** 0–100: mean of per-category scores in {@link #domainScoresJson}. */
    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    /** JSON object: category code → score 0–100. */
    @Column(name = "domain_scores_json", nullable = false, columnDefinition = "TEXT")
    private String domainScoresJson;

    /** JSON array of short strings, e.g. "Java OOP (85%)". */
    @Column(name = "strengths_json", nullable = false, columnDefinition = "TEXT")
    private String strengthsJson;

    /** JSON array of short strings for relative gaps / underperforming areas. */
    @Column(name = "weaknesses_json", nullable = false, columnDefinition = "TEXT")
    private String weaknessesJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;
}
