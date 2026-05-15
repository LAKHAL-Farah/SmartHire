package tn.esprit.msinterview.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.msinterview.controller.support.InterviewRequestUserResolver;
import tn.esprit.msinterview.dto.QuestionBookmarkDTO;
import tn.esprit.msinterview.dto.DTOMapper;
import tn.esprit.msinterview.entity.QuestionBookmark;
import tn.esprit.msinterview.repository.QuestionBookmarkRepository;
import tn.esprit.msinterview.service.QuestionBookmarkService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class QuestionBookmarkController {

    private final QuestionBookmarkService questionBookmarkService;
    private final QuestionBookmarkRepository questionBookmarkRepository;
    private final InterviewRequestUserResolver requestUserResolver;

    @PostMapping
    public ResponseEntity<QuestionBookmarkDTO> addBookmark(HttpServletRequest httpRequest, @RequestBody Map<String, Object> request) {
        Long requestedUserId = request.get("userId") == null
                ? null
                : Long.valueOf(request.get("userId").toString());
        Long userId = requestUserResolver.resolveAndAssertUserId(requestedUserId, httpRequest);
        Long questionId = Long.valueOf(request.get("questionId").toString());
        String note = (String) request.get("note");
        String tagLabel = (String) request.get("tagLabel");

        QuestionBookmark bookmark = questionBookmarkService.addBookmark(userId, questionId, note, tagLabel);
        return ResponseEntity.status(HttpStatus.CREATED).body(DTOMapper.toBookmarkDTO(bookmark));
    }

    @DeleteMapping("/user/{userId}/question/{questionId}")
    public ResponseEntity<Void> removeBookmark(HttpServletRequest request, @PathVariable Long userId, @PathVariable Long questionId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        questionBookmarkService.removeBookmark(resolvedUserId, questionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<QuestionBookmarkDTO>> getBookmarksByUser(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        List<QuestionBookmark> bookmarks = questionBookmarkService.getBookmarksByUser(resolvedUserId);
        return ResponseEntity.ok(bookmarks.stream().map(DTOMapper::toBookmarkDTO).toList());
    }

    @GetMapping("/user/{userId}/tag/{tag}")
    public ResponseEntity<List<QuestionBookmarkDTO>> getBookmarksByTag(
            HttpServletRequest request,
            @PathVariable Long userId,
            @PathVariable String tag) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        List<QuestionBookmark> bookmarks = questionBookmarkService.getBookmarksByTag(resolvedUserId, tag);
        return ResponseEntity.ok(bookmarks.stream().map(DTOMapper::toBookmarkDTO).toList());
    }

    @GetMapping("/user/{userId}/question/{questionId}/exists")
    public ResponseEntity<Boolean> isBookmarked(HttpServletRequest request, @PathVariable Long userId, @PathVariable Long questionId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        boolean exists = questionBookmarkService.isBookmarked(resolvedUserId, questionId);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/user/{userId}/tags")
    public ResponseEntity<List<String>> getUserTags(HttpServletRequest request, @PathVariable Long userId) {
        Long resolvedUserId = requestUserResolver.resolveAndAssertUserId(userId, request);
        List<String> tags = questionBookmarkService.getUserTags(resolvedUserId);
        return ResponseEntity.ok(tags);
    }

    @PutMapping("/{id}/note")
    public ResponseEntity<QuestionBookmarkDTO> updateNote(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        QuestionBookmark existing = questionBookmarkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bookmark not found."));
        requestUserResolver.assertCurrentUserOwnsUserId(existing.getUserId(), request, "Bookmark");

        String note = requestBody.get("note");
        QuestionBookmark bookmark = questionBookmarkService.updateNote(id, note);
        return ResponseEntity.ok(DTOMapper.toBookmarkDTO(bookmark));
    }
}
