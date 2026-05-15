package tn.esprit.msinterview.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewStreakDTO {
    private Long id;
    private Long userId;
    private Integer currentStreak;
    private Integer longestStreak;
    private LocalDate lastSessionDate;
    private Integer totalSessionsCompleted;
    private LocalDateTime updatedAt;
}
