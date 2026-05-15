package tn.esprit.msroadmap.Controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msroadmap.DTO.request.NodeProjectValidationRequestDto;
import tn.esprit.msroadmap.DTO.request.NodeTutorPromptRequestDto;
import tn.esprit.msroadmap.DTO.request.ReplanRequestDto;
import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.response.NodeCourseContentDto;
import tn.esprit.msroadmap.DTO.response.NodeProjectLabDto;
import tn.esprit.msroadmap.DTO.response.NodeProjectValidationResponseDto;
import tn.esprit.msroadmap.DTO.response.NodeQuizResponseDto;
import tn.esprit.msroadmap.DTO.response.NodeTutorPromptResponseDto;
import tn.esprit.msroadmap.DTO.response.RoadmapVisualResponse;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapVisualService;
import tn.esprit.msroadmap.security.CurrentUserIdResolver;

import java.util.List;

@RestController
@RequestMapping("/api/roadmaps/visual")
@RequiredArgsConstructor
@Tag(name = "Visual Roadmap", description = "AI-powered visual roadmap generation like roadmap.sh")
public class RoadmapVisualController {

    private final IRoadmapVisualService visualService;
    private final CurrentUserIdResolver currentUserIdResolver;

    @PostMapping("/generate")
    @Operation(summary = "Generate AI visual roadmap as interactive node graph")
    public ResponseEntity<RoadmapVisualResponse> generate(@RequestBody RoadmapGenerationRequestDto request) {
        Long userId = currentUserIdResolver.resolveUserId(request.getUserId());
        request.setUserId(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(visualService.generateVisualRoadmap(request));
    }

    @GetMapping("/{roadmapId}/graph")
    @Operation(summary = "Get full roadmap graph with nodes and edges")
    public ResponseEntity<RoadmapVisualResponse> getGraph(@PathVariable Long roadmapId) {
        return ResponseEntity.ok(visualService.getRoadmapGraph(roadmapId));
    }

    @GetMapping("/nodes/{nodeId}/quiz")
    @Operation(summary = "Generate AI quiz for a roadmap node")
    public ResponseEntity<NodeQuizResponseDto> getNodeQuiz(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "5") Integer questionCount) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.generateNodeQuiz(nodeId, resolvedUserId, questionCount));
    }

    @GetMapping("/nodes/{nodeId}/project-lab")
    @Operation(summary = "Generate AI micro-project for a roadmap node")
    public ResponseEntity<NodeProjectLabDto> getNodeProjectLab(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.generateNodeProjectLab(nodeId, resolvedUserId));
    }

    @GetMapping("/nodes/{nodeId}/project-lab/history")
    @Operation(summary = "Get persisted node project labs history")
    public ResponseEntity<List<NodeProjectLabDto>> getNodeProjectLabHistory(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.getNodeProjectLabHistory(nodeId, resolvedUserId));
    }

    @PostMapping("/nodes/{nodeId}/project-lab/validate")
    @Operation(summary = "Validate node project code with AI feedback")
    public ResponseEntity<NodeProjectValidationResponseDto> validateNodeProject(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId,
            @RequestBody NodeProjectValidationRequestDto request) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.validateNodeProjectSubmission(nodeId, resolvedUserId, request));
    }

    @PostMapping("/nodes/{nodeId}/tutor")
    @Operation(summary = "Ask interactive AI tutor for this roadmap node")
    public ResponseEntity<NodeTutorPromptResponseDto> askNodeTutor(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId,
            @RequestBody NodeTutorPromptRequestDto request) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.askNodeTutor(nodeId, resolvedUserId, request));
    }

    @GetMapping("/nodes/{nodeId}/course")
    @Operation(summary = "Get or generate persisted AI course for a roadmap node")
    public ResponseEntity<NodeCourseContentDto> getNodeCourse(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.generateNodeCourse(nodeId, resolvedUserId, refresh));
    }

    @GetMapping("/nodes/{nodeId}/course/history")
    @Operation(summary = "Get course generation history for a roadmap node")
    public ResponseEntity<List<NodeCourseContentDto>> getNodeCourseHistory(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.getNodeCourseHistory(nodeId, resolvedUserId));
    }

    @PutMapping("/nodes/{nodeId}/complete")
    @Operation(summary = "Complete a node and auto-unlock connected nodes")
    public ResponseEntity<RoadmapVisualResponse> completeNode(@PathVariable Long nodeId, @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.completeNode(nodeId, resolvedUserId));
    }

    @PutMapping("/nodes/{nodeId}/start")
    @Operation(summary = "Mark a node as in-progress")
    public ResponseEntity<RoadmapVisualResponse> startNode(
            @PathVariable Long nodeId,
            @RequestParam(required = false) Long userId) {
        Long resolvedUserId = currentUserIdResolver.resolveUserId(userId);
        return ResponseEntity.ok(visualService.startNode(nodeId, resolvedUserId));
    }

    @PostMapping("/{roadmapId}/replan")
    @Operation(summary = "Replan roadmap with updated skill gaps")
    public ResponseEntity<RoadmapVisualResponse> replan(@PathVariable Long roadmapId, @RequestBody ReplanRequestDto request) {
        return ResponseEntity.ok(visualService.replanVisualRoadmap(roadmapId, request));
    }
}
