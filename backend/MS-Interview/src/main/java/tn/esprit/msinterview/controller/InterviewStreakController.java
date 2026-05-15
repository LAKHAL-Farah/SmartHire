package tn.esprit.msinterview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.dto.InterviewStreakDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.InterviewStreak;
import tn.esprit.msinterview.service.InterviewStreakService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/streaks")
@RequiredArgsConstructor
public class InterviewStreakController {

    private final InterviewStreakService interviewStreakService;
    private final InterviewRequestUserResolver requestUserResolver;

    @GetMapping("/user/{userId}")
    public ResponseEntity<InterviewStreakDTO> getStreak(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        InterviewStreak streak = interviewStreakService.getStreakByUser(resolvedUserId);
        return ResponseEntity.ok(DTOMapper.toStreakDTO(streak));
    }

    @PutMapping("/user/{userId}/update")
    public ResponseEntity<InterviewStreakDTO> updateStreak(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        InterviewStreak streak = interviewStreakService.updateAfterSession(resolvedUserId);
        return ResponseEntity.ok(DTOMapper.toStreakDTO(streak));
    }

    @PutMapping("/user/{userId}/reset")
    public ResponseEntity<InterviewStreakDTO> resetStreak(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        InterviewStreak streak = interviewStreakService.resetStreak(resolvedUserId);
        return ResponseEntity.ok(DTOMapper.toStreakDTO(streak));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<InterviewStreakDTO>> getTopStreaks(@RequestParam(defaultValue = "10") int limit) {
        List<InterviewStreak> topStreaks = interviewStreakService.getTopStreaks(limit);
        return ResponseEntity.ok(topStreaks.stream().map(DTOMapper::toStreakDTO).toList());
    }
}
