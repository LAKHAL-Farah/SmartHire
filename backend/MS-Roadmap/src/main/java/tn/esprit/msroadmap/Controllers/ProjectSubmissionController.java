package tn.esprit.msroadmap.Controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.ProjectSubmitRequestDto;
import tn.esprit.msroadmap.DTO.request.RetrySubmissionRequestDto;
import tn.esprit.msroadmap.DTO.response.ProjectSubmissionDto;
import tn.esprit.msroadmap.Entities.ProjectSubmission;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Mapper.ProjectSubmissionMapper;
import tn.esprit.msroadmap.ServicesImpl.IProjectSubmissionService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectSubmissionController {

    private final IProjectSubmissionService projectSubmissionService;
    private final ProjectSubmissionMapper projectSubmissionMapper;
    private final CurrentUserIdResolver currentUserIdResolver;

    @PostMapping("/submit")
    public ResponseEntity<ProjectSubmissionDto> submit(@RequestBody ProjectSubmitRequestDto request) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        Long userId = currentUserIdResolver.resolveUserId(request.getUserId());
        request.setUserId(userId);

        validatePositiveId(userId, "userId");
        validatePositiveId(request.getProjectSuggestionId(), "projectSuggestionId");
        if (request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
            throw new BusinessException("repoUrl is required");
        }

        ProjectSubmission submission = projectSubmissionService.submitProject(
            userId,
                request.getProjectSuggestionId(),
                request.getRepoUrl()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectSubmissionMapper.toDto(submission));
    }

    @GetMapping("/submissions/{submissionId}/review")
    public ResponseEntity<Map<String, Object>> getReview(@PathVariable Long submissionId) {
        validatePositiveId(submissionId, "submissionId");
        String review = projectSubmissionService.getAiReviewResult(submissionId);
        ProjectSubmission submission = projectSubmissionService.getSubmissionById(submissionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("review", review == null ? "" : review);
        response.put("status", submission.getStatus() == null ? null : submission.getStatus().name());
        response.put("score", submission.getScore());
        response.put("readmeScore", submission.getReadmeScore());
        response.put("structureScore", submission.getStructureScore());
        response.put("testScore", submission.getTestScore());
        response.put("ciScore", submission.getCiScore());
        response.put("recommendations", parseRecommendations(submission.getRecommendations()));
        response.put("reviewedAt", submission.getReviewedAt());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submissions/{submissionId}/retry")
    public ResponseEntity<ProjectSubmissionDto> retry(
            @PathVariable Long submissionId,
            @RequestBody RetrySubmissionRequestDto request
    ) {
        validatePositiveId(submissionId, "submissionId");
        if (request == null || request.getRepoUrl() == null || request.getRepoUrl().isBlank()) {
            throw new BusinessException("repoUrl is required");
        }

        ProjectSubmission retried = projectSubmissionService.retrySubmission(submissionId, request.getRepoUrl());
        return ResponseEntity.ok(projectSubmissionMapper.toDto(retried));
    }

    @GetMapping("/submissions/user/{userId}")
    public ResponseEntity<List<ProjectSubmissionDto>> getUserSubmissions(@PathVariable Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        validatePositiveId(resolvedUserId, "userId");
        return ResponseEntity.ok(
            projectSubmissionMapper.toDtoList(projectSubmissionService.getSubmissionsByUserId(resolvedUserId))
        );
    }

    private void validatePositiveId(Long id, String field) {
        if (id == null || id <= 0) {
            throw new BusinessException(field + " must be a positive number");
        }
    }

    private List<String> parseRecommendations(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\n|,"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
