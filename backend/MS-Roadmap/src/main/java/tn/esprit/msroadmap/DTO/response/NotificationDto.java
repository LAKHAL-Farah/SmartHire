package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;
import tn.esprit.msroadmap.Enums.NotificationType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private Long userId;
    private Long roadmapId;
    private NotificationType type;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
