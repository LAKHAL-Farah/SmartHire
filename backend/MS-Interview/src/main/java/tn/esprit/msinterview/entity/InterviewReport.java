package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "interview_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Column(nullable = false)
    private Long userId;

    private Double finalScore;
    private Double percentileRank;
    private Double contentAvg;
    private Double voiceAvg;
    private Double technicalAvg;
    private Double presenceAvg;
    private String overallStressLevel;
    private Double avgStressScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String questionStressScores;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String recruiterVerdict;

    private String pdfUrl;
    private LocalDateTime generatedAt;
}
