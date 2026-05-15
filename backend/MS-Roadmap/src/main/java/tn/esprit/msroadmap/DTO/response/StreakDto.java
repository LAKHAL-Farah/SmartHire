package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreakDto {
    private int currentStreak;
    private int longestStreak;
    private LocalDate lastActivityDate;
}
