package tn.esprit.msinterview.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewReportDTO {
    private Long id;
    private Long sessionId;
    private Long userId;
    private Double finalScore;
    private Double percentileRank;
    private Double contentAvg;
    private Double voiceAvg;
    private Double technicalAvg;
    private Double presenceAvg;
    private String overallStressLevel;
    private Double avgStressScore;
    private List<QuestionStressScoreDTO> questionStressScores;
    private String strengths;
    private String weaknesses;
    private String recommendations;
    private String recruiterVerdict;
    private String pdfUrl;
    private LocalDateTime generatedAt;
}
