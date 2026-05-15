package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyBuddyMessageDto {
    private Long id;
    private Long userId;
    private Long stepId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
