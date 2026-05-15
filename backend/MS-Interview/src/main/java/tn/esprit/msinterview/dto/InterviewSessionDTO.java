package tn.esprit.msinterview.dto;

import lombok.*;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;
import tn.esprit.msinterview.entity.enumerated.InterviewMode;
import tn.esprit.msinterview.entity.enumerated.InterviewType;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSessionDTO {
    private Long id;
    private Long userId;
    private Long careerPathId;
    private RoleType roleType;
    private InterviewMode mode;
    private InterviewType type;
    private SessionStatus status;
    private Double totalScore;
    private Integer currentQuestionIndex;
    private Integer durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private boolean isPressureMode;
    private Integer pressureEventsTriggered;
    private String ttsAudioUrl;
    
    // Nested DTOs (light versions to avoid circular refs)
    private List<SessionQuestionOrderDTO> questionOrders;
    private List<SessionAnswerDTO> answers;
    private List<PressureEventDTO> pressureEvents;
    private InterviewReportDTO report;
}
