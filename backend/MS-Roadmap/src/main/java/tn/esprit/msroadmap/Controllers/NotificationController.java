package tn.esprit.msroadmap.Controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.response.NotificationDto;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Mapper.NotificationMapper;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapNotificationService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management")
public class NotificationController {

    private final IRoadmapNotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final CurrentUserIdResolver currentUserIdResolver;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all notifications for a user")
    public ResponseEntity<List<NotificationDto>> getByUser(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(resolvedUserId, "userId");
        return ResponseEntity.ok(notificationMapper.toDtoList(
            notificationService.getNotificationsForUser(resolvedUserId)));
    }

    @GetMapping("/roadmap/{roadmapId}")
    @Operation(summary = "Get notifications for a specific roadmap")
    public ResponseEntity<List<NotificationDto>> getByRoadmap(
            @PathVariable Long roadmapId,
            @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(roadmapId, "roadmapId");
        validatePositiveId(resolvedUserId, "userId");

        return ResponseEntity.ok(notificationMapper.toDtoList(
            notificationService.getNotificationsForUserAndRoadmap(resolvedUserId, roadmapId)));
    }

    @GetMapping("/user/{userId}/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(resolvedUserId, "userId");
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(resolvedUserId)));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<NotificationDto> markAsRead(@PathVariable Long notificationId) {
        validatePositiveId(notificationId, "notificationId");
        return ResponseEntity.ok(notificationMapper.toDto(
                notificationService.markAsRead(notificationId)));
    }

    @PutMapping("/user/{userId}/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(resolvedUserId, "userId");
        notificationService.markAllAsRead(resolvedUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete notification")
    public ResponseEntity<Void> delete(@PathVariable Long notificationId) {
        validatePositiveId(notificationId, "notificationId");
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    private void validatePositiveId(Long id, String field) {
        if (id == null || id <= 0) {
            throw new BusinessException(field + " must be a positive number");
        }
    }
}
