package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_streaks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewStreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private Integer currentStreak;
    private Integer longestStreak;
    private LocalDate lastSessionDate;
    private Integer totalSessionsCompleted;
    private LocalDateTime updatedAt;
}
