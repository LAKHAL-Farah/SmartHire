package tn.esprit.msroadmap.Controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.msroadmap.DTO.response.StreakDto;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

@RestController
@RequestMapping("/api/streaks")
@RequiredArgsConstructor
@Tag(name = "Streaks", description = "Roadmap streak insights")
public class StreakController {

    private final RoadmapRepository roadmapRepository;
    private final CurrentUserIdResolver currentUserIdResolver;

    @GetMapping("/user/{userId}/roadmap/{roadmapId}")
    @Operation(summary = "Get user streak info for a roadmap")
    public ResponseEntity<StreakDto> getStreak(
            @PathVariable Long userId,
            @PathVariable Long roadmapId
    ) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositive(resolvedUserId, "userId");
        validatePositive(roadmapId, "roadmapId");

        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        if (roadmap.getUserId() == null || !roadmap.getUserId().equals(resolvedUserId)) {
            throw new ResourceNotFoundException("Roadmap not found for user");
        }

        StreakDto dto = StreakDto.builder()
                .currentStreak(roadmap.getStreakDays())
                .longestStreak(roadmap.getLongestStreak())
                .lastActivityDate(roadmap.getLastActivityDate())
                .build();

        return ResponseEntity.ok(dto);
    }

    private void validatePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(field + " must be a positive number");
        }
    }
}
