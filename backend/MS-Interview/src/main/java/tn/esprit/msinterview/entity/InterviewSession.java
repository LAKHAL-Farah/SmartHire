package tn.esprit.msinterview.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msinterview.entity.enumerated.InterviewMode;
import tn.esprit.msinterview.entity.enumerated.InterviewType;
import tn.esprit.msinterview.entity.enumerated.LiveSubMode;
import tn.esprit.msinterview.entity.enumerated.RoleType;
import tn.esprit.msinterview.entity.enumerated.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "interview_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long careerPathId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType roleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    private Double totalScore;
    private Integer currentQuestionIndex;
    private Integer durationSeconds;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("isPressureMode")
    @com.fasterxml.jackson.annotation.JsonAlias("pressureMode")
    private boolean isPressureMode;
    private Integer pressureEventsTriggered;

    @Column(name = "is_live_mode")
    private Boolean liveMode = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "live_sub_mode")
    private LiveSubMode liveSubMode;

    @Column(name = "question_count_requested")
    private Integer questionCountRequested;

    @Column(name = "silence_threshold_ms")
    private Integer silenceThresholdMs = 4500;

    @Column(name = "retry_count_total")
    private Integer retryCountTotal = 0;

    @Column
    private Double overallStressScore;

    @JsonIgnore
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private List<SessionQuestionOrder> questionOrders;

    @JsonIgnore
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private List<SessionAnswer> answers;

    @JsonIgnore
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private List<PressureEvent> pressureEvents;

    @JsonIgnore
    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL)
    private InterviewReport report;
}
