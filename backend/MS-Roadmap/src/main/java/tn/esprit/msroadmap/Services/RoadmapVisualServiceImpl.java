package tn.esprit.msroadmap.Services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msroadmap.DTO.request.ReplanRequestDto;
import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.request.NodeProjectValidationRequestDto;
import tn.esprit.msroadmap.DTO.request.NodeTutorPromptRequestDto;
import tn.esprit.msroadmap.DTO.response.NodeCourseCheckpointDto;
import tn.esprit.msroadmap.DTO.response.NodeCourseContentDto;
import tn.esprit.msroadmap.DTO.response.NodeCourseLessonDto;
import tn.esprit.msroadmap.DTO.response.NodeProjectLabDto;
import tn.esprit.msroadmap.DTO.response.NodeQuizQuestionDto;
import tn.esprit.msroadmap.DTO.response.NodeQuizResponseDto;
import tn.esprit.msroadmap.DTO.response.NodeProjectValidationResponseDto;
import tn.esprit.msroadmap.DTO.response.NodeTutorPromptResponseDto;
import tn.esprit.msroadmap.DTO.response.RoadmapEdgeDto;
import tn.esprit.msroadmap.DTO.response.RoadmapNodeDto;
import tn.esprit.msroadmap.DTO.response.RoadmapVisualResponse;
import tn.esprit.msroadmap.Entities.NodeCourseContent;
import tn.esprit.msroadmap.Entities.NodeProjectLabHistory;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Entities.RoadmapEdge;
import tn.esprit.msroadmap.Entities.RoadmapNode;
import tn.esprit.msroadmap.Entities.RoadmapStep;
import tn.esprit.msroadmap.Enums.DifficultyLevel;
import tn.esprit.msroadmap.Enums.EdgeType;
import tn.esprit.msroadmap.Enums.NodeType;
import tn.esprit.msroadmap.Enums.NotificationType;
import tn.esprit.msroadmap.Enums.RoadmapStatus;
import tn.esprit.msroadmap.Enums.StepStatus;
import tn.esprit.msroadmap.Exception.BusinessException;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.NodeCourseContentRepository;
import tn.esprit.msroadmap.Repositories.NodeProjectLabHistoryRepository;
import tn.esprit.msroadmap.Repositories.RoadmapEdgeRepository;
import tn.esprit.msroadmap.Repositories.RoadmapNodeRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.Repositories.RoadmapStepRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapMilestoneService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapNotificationService;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapVisualService;
import tn.esprit.msroadmap.ai.AiClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoadmapVisualServiceImpl implements IRoadmapVisualService {

    private static final int QUIZ_PASS_THRESHOLD = 70;
    private static final int PROJECT_PASS_THRESHOLD = 70;

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapNodeRepository nodeRepository;
    private final RoadmapEdgeRepository edgeRepository;
    private final RoadmapStepRepository stepRepository;
    private final NodeProjectLabHistoryRepository nodeProjectLabHistoryRepository;
    private final NodeCourseContentRepository nodeCourseContentRepository;
    private final IRoadmapMilestoneService milestoneService;
    private final IRoadmapNotificationService notificationService;

    @Override
    public RoadmapVisualResponse generateVisualRoadmap(RoadmapGenerationRequestDto request) {
        if (request.getCareerPathName() == null || request.getCareerPathName().isBlank()) {
            throw new BusinessException("careerPathName is required");
        }
        if (request.getExperienceLevel() == null || request.getExperienceLevel().isBlank()) {
            throw new BusinessException("experienceLevel is required");
        }
        if (request.getWeeklyHoursAvailable() <= 0) {
            throw new BusinessException("weeklyHoursAvailable must be positive");
        }

        String skillGaps = request.getSkillGaps() == null ? "" : String.join(", ", request.getSkillGaps());
        String strongSkills = request.getStrongSkills() == null ? "" : String.join(", ", request.getStrongSkills());
        String aiResponse = aiClient.generateRoadmap(
                request.getCareerPathName(),
                skillGaps,
                strongSkills,
                request.getExperienceLevel(),
                request.getWeeklyHoursAvailable()
        );

        Map<String, Object> parsed;
        try {
            String cleaned = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("AI returned invalid roadmap JSON: " + e.getMessage());
        }

        Roadmap roadmap = new Roadmap();
        roadmap.setUserId(request.getUserId());
        roadmap.setCareerPathId(request.getCareerPathId());
        roadmap.setTitle((String) parsed.get("title"));
        roadmap.setStatus(RoadmapStatus.GENERATING);
        roadmap.setAiGenerated(true);
        roadmap.setGeneratedAt(LocalDateTime.now());
        roadmap = roadmapRepository.save(roadmap);

        List<Map<String, Object>> nodesData = (List<Map<String, Object>>) parsed.getOrDefault("nodes", new ArrayList<>());
        List<Map<String, Object>> edgesData = (List<Map<String, Object>>) parsed.getOrDefault("edges", new ArrayList<>());
        Map<String, RoadmapNode> nodeByNodeId = new HashMap<>();

        for (Map<String, Object> n : nodesData) {
            RoadmapNode node = new RoadmapNode();
            node.setRoadmap(roadmap);
            node.setNodeId((String) n.get("id"));
            node.setTitle((String) n.get("title"));
            node.setDescription((String) n.get("description"));
            node.setObjective((String) n.get("objective"));
            node.setTechnologies((String) n.get("technologies"));
            node.setStepOrder(((Number) n.getOrDefault("stepOrder", 1)).intValue());
            node.setEstimatedDays(((Number) n.getOrDefault("estimatedDays", 0)).intValue());
            node.setPositionX(((Number) n.getOrDefault("x", 0)).doubleValue());
            node.setPositionY(((Number) n.getOrDefault("y", 0)).doubleValue());

            node.setType(parseNodeType(n.get("type")));
            node.setDifficulty(parseDifficulty(n.get("difficulty")));

            node.setStatus(StepStatus.LOCKED);

            RoadmapNode savedNode = nodeRepository.save(node);
            nodeByNodeId.put(savedNode.getNodeId(), savedNode);

            RoadmapStep step = new RoadmapStep();
            step.setRoadmap(roadmap);
            step.setStepOrder(savedNode.getStepOrder());
            step.setTitle(savedNode.getTitle());
            step.setObjective(savedNode.getObjective() != null ? savedNode.getObjective() : savedNode.getDescription());
            step.setEstimatedDays(savedNode.getEstimatedDays());
            step.setStatus(savedNode.getStatus());
            step.setUnlockedAt(savedNode.getUnlockedAt());
            stepRepository.save(step);
        }

        for (Map<String, Object> e : edgesData) {
            RoadmapEdge edge = new RoadmapEdge();
            edge.setRoadmap(roadmap);
            edge.setFromNodeId((String) e.get("from"));
            edge.setToNodeId((String) e.get("to"));
            edge.setType(parseEdgeType(e.get("type")));
            edge.setFromNode(nodeByNodeId.get(edge.getFromNodeId()));
            edgeRepository.save(edge);
        }

        unlockEligibleNodes(roadmap.getId());

        roadmap.setTotalSteps(nodesData.size());
        roadmap.setCompletedSteps(0);
        roadmap.setStatus(RoadmapStatus.ACTIVE);
        roadmapRepository.save(roadmap);

        return buildVisualResponse(roadmap);
    }

    @Override
    public RoadmapVisualResponse generateVisualRoadmapWithNemotron(RoadmapGenerationRequestDto request) {
        return generateVisualRoadmap(request);
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapVisualResponse getRoadmapGraph(Long roadmapId) {
        Roadmap roadmap = roadmapRepository
                .findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found: " + roadmapId));
        return buildVisualResponse(roadmap);
    }

    @Override
    public NodeQuizResponseDto generateNodeQuiz(Long nodeId, Long userId, Integer questionCount) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        int safeQuestionCount = questionCount == null ? 5 : Math.max(3, Math.min(8, questionCount));

        RoadmapNode node = nodeRepository.findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        unlockEligibleNodes(node.getRoadmap().getId());

        node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        if (node.getStatus() == StepStatus.LOCKED) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        List<NodeQuizQuestionDto> questions = generateAiQuizQuestions(node, safeQuestionCount);
        boolean aiGenerated = true;

        if (questions.isEmpty()) {
            questions = generateFallbackQuizQuestions(node, safeQuestionCount);
            aiGenerated = false;
        }

        return NodeQuizResponseDto.builder()
                .nodeId(node.getId())
                .nodeTitle(node.getTitle())
                .passThreshold(QUIZ_PASS_THRESHOLD)
                .aiGenerated(aiGenerated)
                .questions(questions)
                .build();
    }

    @Override
    public NodeProjectLabDto generateNodeProjectLab(Long nodeId, Long userId) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        RoadmapNode node = nodeRepository.findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        unlockEligibleNodes(node.getRoadmap().getId());

        node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())
                || node.getStatus() == StepStatus.LOCKED) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        NodeProjectLabDto projectLab = generateAiNodeProjectLab(node);
        if (projectLab == null) {
            projectLab = generateFallbackNodeProjectLab(node);
        }

        NodeProjectLabHistory persisted = persistProjectLabHistory(node, userId, projectLab);
        return toProjectLabDto(persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NodeProjectLabDto> getNodeProjectLabHistory(Long nodeId, Long userId) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        RoadmapNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        return nodeProjectLabHistoryRepository
                .findTop12ByNodeIdAndUserIdOrderByGeneratedAtDesc(nodeId, userId)
                .stream()
                .map(this::toProjectLabDto)
                .collect(Collectors.toList());
    }

    @Override
    public NodeProjectValidationResponseDto validateNodeProjectSubmission(
            Long nodeId,
            Long userId,
            NodeProjectValidationRequestDto request) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        if (request == null) {
            throw new BusinessException("request body is required");
        }

        String code = request.getCode() == null ? "" : request.getCode().trim();
        if (code.isBlank()) {
            throw new BusinessException("code is required for validation");
        }

        RoadmapNode node = nodeRepository.findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        unlockEligibleNodes(node.getRoadmap().getId());

        node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())
                || node.getStatus() == StepStatus.LOCKED) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        NodeProjectValidationResponseDto aiValidation = generateAiNodeProjectValidation(node, request, code);
        if (aiValidation != null) {
            return aiValidation;
        }

        return generateFallbackNodeProjectValidation(node, request, code);
    }

    @Override
    public NodeTutorPromptResponseDto askNodeTutor(Long nodeId, Long userId, NodeTutorPromptRequestDto request) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new BusinessException("prompt is required");
        }

        String prompt = request.getPrompt().trim();

        RoadmapNode node = nodeRepository.findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);
        unlockEligibleNodes(node.getRoadmap().getId());

        node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())
                || node.getStatus() == StepStatus.LOCKED) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        NodeTutorPromptResponseDto aiResponse = generateAiTutorResponse(node, prompt);
        if (aiResponse != null) {
            return aiResponse;
        }

        return generateFallbackTutorResponse(node, prompt);
    }

    @Override
    public NodeCourseContentDto generateNodeCourse(Long nodeId, Long userId, boolean refresh) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        RoadmapNode node = nodeRepository.findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);
        unlockEligibleNodes(node.getRoadmap().getId());

        node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())
                || node.getStatus() == StepStatus.LOCKED) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        if (!refresh) {
            var latest = nodeCourseContentRepository.findFirstByNodeIdAndUserIdOrderByGeneratedAtDesc(nodeId, userId);
            if (latest.isPresent()) {
                return toNodeCourseDto(latest.get());
            }
        }

        NodeCourseContentDto generatedCourse = generateAiNodeCourse(node);
        if (generatedCourse == null) {
            generatedCourse = generateFallbackNodeCourse(node);
        }

        NodeCourseContent persisted = persistNodeCourse(node, userId, generatedCourse);
        return toNodeCourseDto(persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NodeCourseContentDto> getNodeCourseHistory(Long nodeId, Long userId) {
        if (nodeId == null || nodeId <= 0) {
            throw new BusinessException("nodeId must be a positive number");
        }

        RoadmapNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        return nodeCourseContentRepository
                .findTop12ByNodeIdAndUserIdOrderByGeneratedAtDesc(nodeId, userId)
                .stream()
                .map(this::toNodeCourseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoadmapVisualResponse startNode(Long nodeId, Long userId) {
        RoadmapNode node = nodeRepository.findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        unlockEligibleNodes(node.getRoadmap().getId());

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        if (node.getStatus() != StepStatus.AVAILABLE) {
            throw new BusinessException("Node cannot be started. Current status: " + node.getStatus());
        }

        node.setStatus(StepStatus.IN_PROGRESS);
        if (node.getUnlockedAt() == null) {
            node.setUnlockedAt(LocalDateTime.now());
        }
        nodeRepository.save(node);
        syncClassicStepFromNode(node);

        Roadmap roadmap = roadmapRepository.findByIdForUpdate(node.getRoadmap().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found: " + node.getRoadmap().getId()));
        LocalDate today = LocalDate.now();
        roadmap.setLastActivityDate(today);
        roadmapRepository.save(roadmap);

        return buildVisualResponse(roadmap);
    }

    @Override
    public RoadmapVisualResponse completeNode(Long nodeId, Long userId) {

        RoadmapNode node = nodeRepository
                .findByIdForUpdate(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node not found: " + nodeId));

        validateOwnership(node.getRoadmap(), userId);

        unlockEligibleNodes(node.getRoadmap().getId());

        if (!hasRequiredPredecessorsCompleted(node.getRoadmap().getId(), node.getNodeId())) {
            throw new BusinessException("Node is locked. Complete required previous nodes first.");
        }

        if (node.getStatus() != StepStatus.AVAILABLE && node.getStatus() != StepStatus.IN_PROGRESS) {
            throw new BusinessException("Node is not available for completion");
        }

        node.setStatus(StepStatus.COMPLETED);
        node.setCompletedAt(LocalDateTime.now());

        if (node.getUnlockedAt() != null) {
            long days = ChronoUnit.DAYS.between(node.getUnlockedAt(), LocalDateTime.now());
            node.setActualDays((int) Math.max(1, days));
        }
        nodeRepository.save(node);
        syncClassicStepFromNode(node);

        Long roadmapId = node.getRoadmap().getId();
        List<RoadmapEdge> outgoing = edgeRepository.findByRoadmapIdAndFromNode_Id(roadmapId, node.getId());
        if (outgoing.isEmpty()) {
            outgoing = edgeRepository.findByRoadmapIdAndFromNodeId(roadmapId, node.getNodeId());
        }

        unlockEligibleNodes(roadmapId);

        Roadmap roadmap = roadmapRepository.findByIdForUpdate(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found: " + roadmapId));
        int completed = nodeRepository.countByRoadmapIdAndStatus(roadmapId, StepStatus.COMPLETED);
        roadmap.setCompletedSteps(completed);

        if (completed >= roadmap.getTotalSteps()) {
            roadmap.setStatus(RoadmapStatus.COMPLETED);
        }

        RoadmapProgressCalculator.updateStreakDays(roadmap, LocalDate.now());
        roadmapRepository.save(roadmap);

        milestoneService.checkAndUnlockMilestones(roadmapId);

        notificationService.createNotification(
                userId,
                roadmapId,
                NotificationType.STEP_UNLOCK,
            "Completed node: " + node.getTitle() + ". Next nodes unlocked where available."
        );

        return buildVisualResponse(roadmap);
    }

    @Override
    public RoadmapVisualResponse replanVisualRoadmap(Long roadmapId, ReplanRequestDto request) {

        Roadmap roadmap = roadmapRepository
                .findById(roadmapId)
                .orElseThrow(() -> new ResourceNotFoundException("Roadmap not found: " + roadmapId));

        List<RoadmapNode> completedNodes = nodeRepository.findByRoadmapIdAndStatus(roadmapId, StepStatus.COMPLETED);
        String completedTitles = completedNodes.stream().map(RoadmapNode::getTitle).collect(Collectors.joining(", "));
        log.info("Completed nodes before replan: {}", completedTitles);

        RoadmapGenerationRequestDto newRequest = new RoadmapGenerationRequestDto();
        newRequest.setUserId(roadmap.getUserId());
        newRequest.setCareerPathId(roadmap.getCareerPathId());
        newRequest.setCareerPathName(roadmap.getTitle() == null ? "Career" : roadmap.getTitle().replace(" Roadmap", ""));
        newRequest.setSkillGaps(request.getNewSkillGaps());
        newRequest.setStrongSkills(request.getNewStrongSkills());
        newRequest.setExperienceLevel(request.getExperienceLevel());
        newRequest.setWeeklyHoursAvailable(
            request.getWeeklyHoursAvailable() != null
                ? request.getWeeklyHoursAvailable()
                : Math.max(1, roadmap.getEstimatedWeeks() != null ? roadmap.getEstimatedWeeks() : 1)
        );
        newRequest.setPreferredLanguage(
            request.getPreferredLanguage() != null && !request.getPreferredLanguage().isBlank()
                ? request.getPreferredLanguage().trim().toUpperCase(Locale.ROOT)
                : Locale.getDefault().getLanguage().toUpperCase(Locale.ROOT)
        );

        return generateVisualRoadmap(newRequest);
    }

    private RoadmapVisualResponse buildVisualResponse(Roadmap roadmap) {
        List<RoadmapNode> nodes = nodeRepository.findByRoadmapIdOrderByStepOrderAsc(roadmap.getId());
        List<RoadmapEdge> edges = edgeRepository.findByRoadmapId(roadmap.getId());

        List<RoadmapNodeDto> nodeDtos = nodes.stream().map(this::toNodeDto).collect(Collectors.toList());
        List<RoadmapEdgeDto> edgeDtos = edges.stream().map(this::toEdgeDto).collect(Collectors.toList());

        double progress = roadmap.getTotalSteps() == 0 ? 0.0 : (roadmap.getCompletedSteps() * 100.0) / roadmap.getTotalSteps();

        return RoadmapVisualResponse.builder()
                .roadmapId(roadmap.getId())
                .title(roadmap.getTitle())
                .description(roadmap.getDifficulty())
                .status(roadmap.getStatus())
                .totalNodes(roadmap.getTotalSteps())
                .completedNodes(roadmap.getCompletedSteps())
                .progressPercent(progress)
                .streakDays(roadmap.getStreakDays())
                .longestStreak(roadmap.getLongestStreak())
                .nodes(nodeDtos)
                .edges(edgeDtos)
                .build();
    }

    private RoadmapNodeDto toNodeDto(RoadmapNode n) {
        return RoadmapNodeDto.builder()
                .id(n.getId())
                .nodeId(n.getNodeId())
                .title(n.getTitle())
                .description(n.getDescription())
                .objective(n.getObjective())
                .type(n.getType())
                .difficulty(n.getDifficulty())
                .status(n.getStatus())
                .stepOrder(n.getStepOrder())
                .estimatedDays(n.getEstimatedDays())
                .actualDays(n.getActualDays())
                .technologies(n.getTechnologies())
                .positionX(n.getPositionX())
                .positionY(n.getPositionY())
                .unlockedAt(n.getUnlockedAt())
                .completedAt(n.getCompletedAt())
                .build();
    }

    private RoadmapEdgeDto toEdgeDto(RoadmapEdge e) {
        return RoadmapEdgeDto.builder()
                .id(e.getId())
                .fromNodeId(e.getFromNodeId())
                .toNodeId(e.getToNodeId())
                .type(e.getType())
                .build();
    }

    private void validateOwnership(Roadmap roadmap, Long userId) {
        if (userId == null) {
            throw new BusinessException("userId is required");
        }

        if (roadmap.getUserId() != null && !roadmap.getUserId().equals(userId)) {
            throw new BusinessException("User does not own this roadmap");
        }
    }

    private void syncClassicStepFromNode(RoadmapNode node) {
        stepRepository.findByRoadmapIdAndStepOrderForUpdate(node.getRoadmap().getId(), node.getStepOrder())
                .ifPresent(step -> {
                    step.setStatus(node.getStatus());
                    step.setUnlockedAt(node.getUnlockedAt());
                    step.setCompletedAt(node.getCompletedAt());
                    step.setActualDays(node.getActualDays());
                    stepRepository.save(step);
                });
    }

    private void unlockEligibleNodes(Long roadmapId) {
        List<RoadmapNode> nodes = nodeRepository.findByRoadmapIdOrderByStepOrderAsc(roadmapId);
        for (RoadmapNode candidate : nodes) {
            if (candidate.getStatus() != StepStatus.LOCKED) {
                continue;
            }

            if (!hasRequiredPredecessorsCompleted(roadmapId, candidate.getNodeId())) {
                continue;
            }

            candidate.setStatus(StepStatus.AVAILABLE);
            if (candidate.getUnlockedAt() == null) {
                candidate.setUnlockedAt(LocalDateTime.now());
            }
            nodeRepository.save(candidate);
            syncClassicStepFromNode(candidate);
        }
    }

    private boolean hasRequiredPredecessorsCompleted(Long roadmapId, String targetNodeId) {
        List<RoadmapEdge> incomingEdges = edgeRepository.findByRoadmapIdAndToNodeId(roadmapId, targetNodeId);

        List<RoadmapEdge> requiredIncoming = incomingEdges.stream()
            .filter(edge -> edge.getType() == null || edge.getType() == EdgeType.REQUIRED)
            .toList();

        if (requiredIncoming.isEmpty()) {
            return true;
        }

        for (RoadmapEdge edge : requiredIncoming) {
            RoadmapNode predecessor = nodeRepository
                .findByRoadmapIdAndNodeId(roadmapId, edge.getFromNodeId())
                .orElse(null);

            if (predecessor == null || predecessor.getStatus() != StepStatus.COMPLETED) {
                return false;
            }
        }

        return true;
    }

    private List<NodeQuizQuestionDto> generateAiQuizQuestions(RoadmapNode node, int questionCount) {
        String topic = node.getTitle() == null || node.getTitle().isBlank() ? "Roadmap Topic" : node.getTitle();
        String objective = node.getObjective() == null || node.getObjective().isBlank()
                ? "Validate practical understanding"
                : node.getObjective();
        String description = node.getDescription() == null ? "" : node.getDescription();
        String technologies = node.getTechnologies() == null ? "" : node.getTechnologies();

        String systemPrompt = "You are a senior technical interviewer. "
                + "Generate professional multiple-choice quiz questions. "
                + "Return only valid JSON. No markdown, no explanations.";

        String userPrompt = String.format("""
                Generate %d multiple-choice questions for this learning node.

                Topic: %s
                Objective: %s
                Description: %s
                Technologies: %s
                Difficulty: %s

                Return ONLY JSON in this exact shape:
                {
                  "questions": [
                    {
                      "id": "q1",
                      "prompt": "Question text",
                      "correct": "One correct answer option",
                      "distractors": ["wrong option 1", "wrong option 2", "wrong option 3"]
                    }
                  ]
                }

                Requirements:
                - Exactly 4 options per question (1 correct + 3 distractors)
                - Questions must test applied understanding, not memorization only
                - Keep each option concise and unambiguous
                - Output must be parseable JSON only
                """, questionCount, topic, objective, description, technologies, node.getDifficulty());

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            String cleaned = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);

            JsonNode questionsNode = root;
            if (root.isObject() && root.has("questions")) {
                questionsNode = root.get("questions");
            }

            if (questionsNode == null || !questionsNode.isArray()) {
                return List.of();
            }

            List<NodeQuizQuestionDto> questions = new ArrayList<>();
            int index = 0;
            for (JsonNode q : questionsNode) {
                if (index >= questionCount) {
                    break;
                }

                String prompt = jsonText(q, "prompt");
                String correct = firstNonBlank(jsonText(q, "correct"), jsonText(q, "correctAnswer"));
                if (prompt.isBlank() || correct.isBlank()) {
                    continue;
                }

                List<String> distractors = jsonStringList(q, "distractors");
                NodeQuizQuestionDto dto = buildQuizQuestion(
                        firstNonBlank(jsonText(q, "id"), "ai-" + node.getId() + "-" + (index + 1)),
                        prompt,
                        correct,
                        distractors,
                        topic);

                if (dto != null) {
                    questions.add(dto);
                    index += 1;
                }
            }

            return questions;
        } catch (Exception ex) {
            log.warn("AI quiz generation failed for node {}: {}", node.getId(), ex.getMessage());
            return List.of();
        }
    }

    private List<NodeQuizQuestionDto> generateFallbackQuizQuestions(RoadmapNode node, int questionCount) {
        String topic = node.getTitle() == null || node.getTitle().isBlank() ? "this topic" : node.getTitle();
        String objective = node.getObjective() == null || node.getObjective().isBlank()
                ? "deliver practical outcomes"
                : node.getObjective();

        List<NodeQuizQuestionDto> questions = new ArrayList<>();
        for (int index = 0; index < questionCount; index++) {
            String prompt;
            String correct;
            List<String> distractors;

            if (index % 4 == 0) {
                prompt = "Which approach best shows practical mastery of " + topic + "?";
                correct = "Implement a small working solution and explain trade-offs clearly.";
                distractors = List.of(
                        "Memorize definitions without implementation practice.",
                        "Skip validation and rely on assumptions.",
                        "Focus only on tools and ignore fundamentals.");
            } else if (index % 4 == 1) {
                prompt = "When applying " + topic + ", what should come first?";
                correct = "Clarify requirements and success criteria before coding.";
                distractors = List.of(
                        "Optimize performance before understanding the problem.",
                        "Copy a random implementation and adjust later.",
                        "Delay testing until all features are complete.");
            } else if (index % 4 == 2) {
                prompt = "Which signal indicates readiness to move past this node?";
                correct = "You can complete a targeted task aligned with: " + objective;
                distractors = List.of(
                        "You watched tutorials but did not practice.",
                        "You can recall terms without applying them.",
                        "You only solved one narrow example.");
            } else {
                prompt = "What is the best debugging strategy while learning " + topic + "?";
                correct = "Reproduce the issue, isolate the root cause, and validate fixes incrementally.";
                distractors = List.of(
                        "Apply multiple changes at once and hope for improvement.",
                        "Ignore failing behavior and continue to the next module.",
                        "Rewrite everything before collecting evidence.");
            }

            NodeQuizQuestionDto question = buildQuizQuestion(
                    "fallback-" + node.getId() + "-" + (index + 1),
                    prompt,
                    correct,
                    distractors,
                    topic);

            if (question != null) {
                questions.add(question);
            }
        }

        return questions;
    }

    private NodeQuizQuestionDto buildQuizQuestion(
            String id,
            String prompt,
            String correct,
            List<String> distractors,
            String topicLabel) {
        String normalizedCorrect = correct == null ? "" : correct.trim();
        if (normalizedCorrect.isBlank()) {
            return null;
        }

        LinkedHashSet<String> optionSet = new LinkedHashSet<>();
        optionSet.add(normalizedCorrect);

        for (String distractor : distractors) {
            String candidate = distractor == null ? "" : distractor.trim();
            if (!candidate.isBlank()) {
                optionSet.add(candidate);
            }
        }

        while (optionSet.size() < 4) {
            optionSet.add(buildGenericDistractor(topicLabel, optionSet.size()));
        }

        List<String> options = new ArrayList<>(optionSet);
        if (options.size() > 4) {
            if (options.subList(0, 4).contains(normalizedCorrect)) {
                options = new ArrayList<>(options.subList(0, 4));
            } else {
                List<String> trimmed = new ArrayList<>(options.subList(0, 3));
                trimmed.add(normalizedCorrect);
                options = trimmed;
            }
        }

        Collections.shuffle(options);
        int correctIndex = options.indexOf(normalizedCorrect);
        if (correctIndex < 0) {
            return null;
        }

        return NodeQuizQuestionDto.builder()
                .id(id)
                .prompt(prompt)
                .options(options)
                .correctIndex(correctIndex)
                .build();
    }

    private List<String> jsonStringList(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : field) {
            String value = item == null ? "" : item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String jsonText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field == null ? "" : field.asText("").trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String buildGenericDistractor(String topicLabel, int index) {
        String topic = (topicLabel == null || topicLabel.isBlank()) ? "this topic" : topicLabel;
        return switch (index % 4) {
            case 0 -> "Rely only on memorization without applying " + topic + ".";
            case 1 -> "Skip validation and assume the first approach is correct.";
            case 2 -> "Avoid testing edge cases and production-like scenarios.";
            default -> "Focus on speed only and ignore maintainability concerns.";
        };
    }

    private NodeProjectLabDto generateAiNodeProjectLab(RoadmapNode node) {
        String topic = firstNonBlank(node.getTitle(), "Roadmap Topic").trim();
        String objective = firstNonBlank(node.getObjective(), "Deliver practical outcomes").trim();
        String description = node.getDescription() == null ? "" : node.getDescription();
        String technologies = node.getTechnologies() == null ? "" : node.getTechnologies();
        String suggestedLanguage = inferProjectLanguage(node);

        String systemPrompt = "You are a project-based learning curriculum designer. "
                + "Create short, hands-on coding micro projects like FreeCodeCamp style tasks. "
                + "Return only valid JSON with no markdown.";

        String userPrompt = String.format("""
                Build one compact project lab for this roadmap node.

                Topic: %s
                Objective: %s
                Description: %s
                Technologies: %s
                Difficulty: %s
                Preferred language: %s

                Return ONLY JSON in this exact shape:
                {
                  "projectTitle": "string",
                  "scenario": "string",
                  "language": "string",
                  "estimatedHours": number,
                  "userStories": ["string"],
                  "acceptanceCriteria": ["string"],
                  "stretchGoals": ["string"],
                  "starterCode": "string"
                }

                Requirements:
                - Create a beginner-friendly but practical project
                - Include 3-5 user stories
                - Include 4-7 acceptance criteria checks
                - Keep it solvable in 1-4 hours
                - Starter code must contain TODO markers
                """, topic, objective, description, technologies, node.getDifficulty(), suggestedLanguage);

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            String cleaned = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode payload = root.isObject() && root.has("project") ? root.get("project") : root;

            if (payload == null || !payload.isObject()) {
                return null;
            }

            String projectTitle = firstNonBlank(jsonText(payload, "projectTitle"), topic + " Mini Project").trim();
            String scenario = firstNonBlank(jsonText(payload, "scenario"),
                    "Build a working artifact that proves understanding of " + topic + ".").trim();
            String language = firstNonBlank(jsonText(payload, "language"), suggestedLanguage).trim();

            List<String> userStories = jsonStringList(payload, "userStories");
            if (userStories.isEmpty()) {
                userStories = fallbackUserStories(topic);
            }

            List<String> acceptanceCriteria = jsonStringList(payload, "acceptanceCriteria");
            if (acceptanceCriteria.isEmpty()) {
                acceptanceCriteria = fallbackAcceptanceCriteria(topic, objective);
            }

            List<String> stretchGoals = jsonStringList(payload, "stretchGoals");
            if (stretchGoals.isEmpty()) {
                stretchGoals = fallbackStretchGoals(topic);
            }

            String starterCode = jsonText(payload, "starterCode");
            if (starterCode.isBlank()) {
                starterCode = buildStarterCodeTemplate(language, projectTitle);
            }

            int estimatedHours = Math.max(1, Math.min(8, jsonInt(payload, "estimatedHours", 2)));

            return NodeProjectLabDto.builder()
                    .nodeId(node.getId())
                    .nodeTitle(node.getTitle())
                    .projectTitle(projectTitle)
                    .scenario(scenario)
                    .language(language)
                    .estimatedHours(estimatedHours)
                    .passThreshold(PROJECT_PASS_THRESHOLD)
                    .aiGenerated(true)
                    .userStories(userStories)
                    .acceptanceCriteria(acceptanceCriteria)
                    .stretchGoals(stretchGoals)
                    .starterCode(starterCode)
                    .generatedAt(LocalDateTime.now())
                    .build();
        } catch (Exception ex) {
            log.warn("AI node project generation failed for node {}: {}", node.getId(), ex.getMessage());
            return null;
        }
    }

    private NodeProjectLabDto generateFallbackNodeProjectLab(RoadmapNode node) {
        String topic = firstNonBlank(node.getTitle(), "Roadmap Topic").trim();
        String objective = firstNonBlank(node.getObjective(), "Build practical outcomes").trim();
        String language = inferProjectLanguage(node);
        String projectTitle = topic + " Hands-On Mini Project";

        return NodeProjectLabDto.builder()
                .nodeId(node.getId())
                .nodeTitle(node.getTitle())
                .projectTitle(projectTitle)
                .scenario("Build a compact solution that demonstrates mastery of " + topic + ".")
                .language(language)
                .estimatedHours(2)
                .passThreshold(PROJECT_PASS_THRESHOLD)
                .aiGenerated(false)
                .userStories(fallbackUserStories(topic))
                .acceptanceCriteria(fallbackAcceptanceCriteria(topic, objective))
                .stretchGoals(fallbackStretchGoals(topic))
                .starterCode(buildStarterCodeTemplate(language, projectTitle))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private NodeProjectValidationResponseDto generateAiNodeProjectValidation(
            RoadmapNode node,
            NodeProjectValidationRequestDto request,
            String code) {
        String topic = firstNonBlank(node.getTitle(), "Roadmap Topic").trim();
        String objective = firstNonBlank(node.getObjective(), "Deliver practical outcomes").trim();
        String technologies = node.getTechnologies() == null ? "" : node.getTechnologies();
        String projectTitle = firstNonBlank(request.getProjectTitle(), topic + " Mini Project").trim();
        String language = firstNonBlank(request.getLanguage(), inferProjectLanguage(node)).trim();
        String acceptanceChecks = request.getAcceptanceCriteria() == null
                ? ""
                : request.getAcceptanceCriteria().stream().filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .collect(Collectors.joining("\n- ", "- ", ""));

        String systemPrompt = "You are a strict coding reviewer for project-based learning checkpoints. "
                + "Evaluate code quality and objective completion. Return only valid JSON.";

        String userPrompt = String.format("""
                Validate this node project submission.

                Topic: %s
                Objective: %s
                Technologies: %s
                Project title: %s
                Language: %s
                Acceptance criteria:
                %s

                Candidate code:
                %s

                Return ONLY JSON:
                {
                  "scorePercent": number,
                  "passed": true|false,
                  "summary": "string",
                  "strengths": ["string"],
                  "improvements": ["string"],
                  "nextSteps": ["string"]
                }

                Rules:
                - scorePercent must be 0..100
                - passed is true only when scorePercent >= %d
                - Keep feedback concrete and actionable
                """, topic, objective, technologies, projectTitle, language, acceptanceChecks, code, PROJECT_PASS_THRESHOLD);

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            String cleaned = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode payload = root.isObject() && root.has("result") ? root.get("result") : root;

            if (payload == null || !payload.isObject()) {
                return null;
            }

            int scorePercent = Math.max(0, Math.min(100, jsonInt(payload, "scorePercent", 0)));
            boolean passed = payload.has("passed")
                    ? payload.get("passed").asBoolean(scorePercent >= PROJECT_PASS_THRESHOLD)
                    : scorePercent >= PROJECT_PASS_THRESHOLD;

            String summary = firstNonBlank(jsonText(payload, "summary"),
                    passed ? "Good submission with acceptable quality." : "Submission needs improvement.");
            List<String> strengths = jsonStringList(payload, "strengths");
            List<String> improvements = jsonStringList(payload, "improvements");
            List<String> nextSteps = jsonStringList(payload, "nextSteps");

            if (strengths.isEmpty()) {
                strengths = List.of("Core structure is present and can be iterated further.");
            }
            if (improvements.isEmpty() && !passed) {
                improvements = List.of("Improve code organization and ensure all acceptance checks are implemented.");
            }
            if (nextSteps.isEmpty()) {
                nextSteps = List.of("Refine the solution and re-run validation.");
            }

            return NodeProjectValidationResponseDto.builder()
                    .nodeId(node.getId())
                    .projectTitle(projectTitle)
                    .passThreshold(PROJECT_PASS_THRESHOLD)
                    .scorePercent(scorePercent)
                    .passed(passed)
                    .aiGenerated(true)
                    .summary(summary)
                    .strengths(strengths)
                    .improvements(improvements)
                    .nextSteps(nextSteps)
                    .build();
        } catch (Exception ex) {
            log.warn("AI node project validation failed for node {}: {}", node.getId(), ex.getMessage());
            return null;
        }
    }

    private NodeProjectValidationResponseDto generateFallbackNodeProjectValidation(
            RoadmapNode node,
            NodeProjectValidationRequestDto request,
            String code) {
        String projectTitle = firstNonBlank(request.getProjectTitle(), firstNonBlank(node.getTitle(), "Mini Project"));

        int score = 35;
        if (code.length() >= 120) {
            score += 20;
        }
        if (code.length() >= 300) {
            score += 15;
        }
        if (code.toLowerCase(Locale.ROOT).contains("test") || code.toLowerCase(Locale.ROOT).contains("assert")) {
            score += 10;
        }

        List<String> keywords = extractKeywords(firstNonBlank(node.getTitle(), "") + " " + firstNonBlank(node.getObjective(), ""));
        if (containsAnyKeyword(code, keywords)) {
            score += 10;
        }

        score = Math.max(0, Math.min(100, score));
        boolean passed = score >= PROJECT_PASS_THRESHOLD;

        List<String> strengths = new ArrayList<>();
        strengths.add("Submission includes executable code structure.");
        if (code.length() >= 300) {
            strengths.add("Solution has meaningful implementation detail.");
        }

        List<String> improvements = new ArrayList<>();
        if (!passed) {
            improvements.add("Cover more acceptance criteria with explicit implementation.");
            improvements.add("Add clearer function/module boundaries and comments for readability.");
        } else {
            improvements.add("Improve edge-case handling to harden production readiness.");
        }

        List<String> nextSteps = List.of(
                "Run your project with sample inputs and verify expected behavior.",
                "Add at least one automated test or assertion check.",
                "Refactor naming and structure for maintainability.");

        return NodeProjectValidationResponseDto.builder()
                .nodeId(node.getId())
                .projectTitle(projectTitle)
                .passThreshold(PROJECT_PASS_THRESHOLD)
                .scorePercent(score)
                .passed(passed)
                .aiGenerated(false)
                .summary(passed
                        ? "Submission meets baseline quality. Great progress."
                        : "Submission does not yet meet the passing threshold. Keep iterating.")
                .strengths(strengths)
                .improvements(improvements)
                .nextSteps(nextSteps)
                .build();
    }

    private List<String> fallbackUserStories(String topic) {
        return List.of(
                "As a learner, I can run the project locally and see a working output for " + topic + ".",
                "As a learner, I can organize the solution into clear, reusable units.",
                "As a learner, I can handle invalid input or error scenarios gracefully.");
    }

    private List<String> fallbackAcceptanceCriteria(String topic, String objective) {
        return List.of(
                "Implements the core objective: " + objective + ".",
                "Uses at least one concept directly related to " + topic + ".",
                "Includes clear input/output behavior and basic validation.",
                "Project can be executed and produces expected results for at least one sample case.");
    }

    private List<String> fallbackStretchGoals(String topic) {
        return List.of(
                "Add automated test coverage for one core scenario.",
                "Expose a cleaner interface or command for using the " + topic + " project.",
                "Optimize readability and refactor duplicated logic.");
    }

    private String inferProjectLanguage(RoadmapNode node) {
        String source = (firstNonBlank(node.getTechnologies(), "") + " "
                + firstNonBlank(node.getTitle(), "") + " "
                + firstNonBlank(node.getDescription(), "")).toLowerCase(Locale.ROOT);

        if (source.contains("java")) return "Java";
        if (source.contains("spring") || source.contains("hibernate") || source.contains("jpa") || source.contains("maven")) {
            return "Java";
        }
        if (source.contains("typescript") || source.contains("angular")) return "TypeScript";
        if (source.contains("python")) return "Python";
        if (source.contains("sql")) return "SQL";
        if (source.contains("c#") || source.contains("dotnet")) return "C#";
        return "JavaScript";
    }

    private String buildStarterCodeTemplate(String language, String projectTitle) {
        String safeTitle = projectTitle == null || projectTitle.isBlank() ? "Node Project" : projectTitle.trim();
        String normalized = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);

        if (normalized.contains("java")) {
            return """
                    public class NodeProjectStarter {
                        public static void main(String[] args) {
                            // TODO: implement %s
                            System.out.println("Start building your solution...");
                        }
                    }
                    """.formatted(safeTitle);
        }

        if (normalized.contains("python")) {
            return """
                    def solve(input_data):
                        # TODO: implement %s
                        return "Start building your solution..."


                    if __name__ == "__main__":
                        print(solve("sample"))
                    """.formatted(safeTitle);
        }

        if (normalized.contains("typescript")) {
            return """
                    type Input = string;

                    function solve(input: Input): string {
                      // TODO: implement %s
                      return 'Start building your solution...';
                    }

                    console.log(solve('sample'));
                    """.formatted(safeTitle);
        }

        if (normalized.contains("sql")) {
            return """
                    -- TODO: implement %s
                    -- Replace table and column names according to your schema.
                    SELECT 'Start building your solution...' AS result;
                    """.formatted(safeTitle);
        }

        return """
                function solve(input) {
                  // TODO: implement %s
                  return 'Start building your solution...';
                }

                console.log(solve('sample'));
                """.formatted(safeTitle);
    }

    private int jsonInt(JsonNode node, String fieldName, int fallback) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return fallback;
        }
        if (field.isInt() || field.isLong()) {
            return field.asInt(fallback);
        }
        if (field.isTextual()) {
            try {
                return Integer.parseInt(field.asText().trim());
            } catch (Exception ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private List<String> extractKeywords(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }

        return List.of(source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .stream()
                .map(String::trim)
                .filter(token -> token.length() >= 4)
                .distinct()
                .limit(10)
                .toList();
    }

    private boolean containsAnyKeyword(String code, List<String> keywords) {
        String normalizedCode = code == null ? "" : code.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalizedCode.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private NodeProjectLabHistory persistProjectLabHistory(RoadmapNode node, Long userId, NodeProjectLabDto dto) {
        LocalDateTime generatedAt = dto.getGeneratedAt() != null ? dto.getGeneratedAt() : LocalDateTime.now();

        NodeProjectLabHistory history = NodeProjectLabHistory.builder()
                .userId(userId)
                .node(node)
                .projectTitle(compactText(firstNonBlank(dto.getProjectTitle(), "Node Project Lab"), 240))
                .scenario(firstNonBlank(dto.getScenario(), "Build a practical artifact for this node."))
                .language(compactText(firstNonBlank(dto.getLanguage(), inferProjectLanguage(node)), 64))
                .estimatedHours(Math.max(1, Math.min(8, dto.getEstimatedHours())))
                .userStoriesJson(writeJsonValue(dto.getUserStories()))
                .acceptanceCriteriaJson(writeJsonValue(dto.getAcceptanceCriteria()))
                .stretchGoalsJson(writeJsonValue(dto.getStretchGoals()))
                .starterCode(dto.getStarterCode())
                .aiGenerated(dto.isAiGenerated())
                .generatedAt(generatedAt)
                .build();

        return nodeProjectLabHistoryRepository.save(history);
    }

    private NodeProjectLabDto toProjectLabDto(NodeProjectLabHistory history) {
        return NodeProjectLabDto.builder()
                .historyId(history.getId())
                .nodeId(history.getNode().getId())
                .nodeTitle(history.getNode().getTitle())
                .projectTitle(firstNonBlank(history.getProjectTitle(), history.getNode().getTitle() + " Mini Project"))
                .scenario(firstNonBlank(history.getScenario(), "Build a practical artifact for this node."))
                .language(firstNonBlank(history.getLanguage(), inferProjectLanguage(history.getNode())))
                .estimatedHours(history.getEstimatedHours() == null ? 2 : history.getEstimatedHours())
                .passThreshold(PROJECT_PASS_THRESHOLD)
                .aiGenerated(history.isAiGenerated())
                .userStories(readStringList(history.getUserStoriesJson()))
                .acceptanceCriteria(readStringList(history.getAcceptanceCriteriaJson()))
                .stretchGoals(readStringList(history.getStretchGoalsJson()))
                .starterCode(firstNonBlank(history.getStarterCode(), ""))
                .generatedAt(history.getGeneratedAt())
                .build();
    }

    private NodeTutorPromptResponseDto generateAiTutorResponse(RoadmapNode node, String prompt) {
        String topic = firstNonBlank(node.getTitle(), "Roadmap Topic").trim();
        String objective = firstNonBlank(node.getObjective(), "Apply the topic with confidence").trim();
        String technologies = firstNonBlank(node.getTechnologies(), "General stack").trim();

        String systemPrompt = "You are a concise technical mentor. "
                + "Teach like W3Schools: practical, clear, and beginner-friendly. "
                + "Return only valid JSON with no markdown.";

        String userPrompt = String.format("""
                Answer this learner prompt for a roadmap node.

                Topic: %s
                Objective: %s
                Technologies: %s
                Learner prompt: %s

                Return ONLY JSON:
                {
                  "answer": "string",
                  "keyTakeaways": ["string"],
                  "nextActions": ["string"]
                }

                Rules:
                - Keep answer concrete and practical
                - Include 2-4 key takeaways
                - Include 2-4 immediate next actions
                """, topic, objective, technologies, prompt);

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            String cleaned = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode payload = root.isObject() && root.has("tutor") ? root.get("tutor") : root;

            if (payload == null || !payload.isObject()) {
                return null;
            }

            String answer = firstNonBlank(jsonText(payload, "answer"), jsonText(payload, "explanation")).trim();
            if (answer.isBlank()) {
                return null;
            }

            List<String> takeaways = jsonStringList(payload, "keyTakeaways");
            if (takeaways.isEmpty()) {
                takeaways = List.of(
                        "Break the topic into small testable steps.",
                        "Practice with one real mini-implementation before moving on.");
            }

            List<String> nextActions = jsonStringList(payload, "nextActions");
            if (nextActions.isEmpty()) {
                nextActions = List.of(
                        "Rephrase what you learned in your own words.",
                        "Implement one focused exercise from this node today.");
            }

            return NodeTutorPromptResponseDto.builder()
                    .nodeId(node.getId())
                    .nodeTitle(node.getTitle())
                    .prompt(prompt)
                    .answer(answer)
                    .keyTakeaways(takeaways)
                    .nextActions(nextActions)
                    .aiGenerated(true)
                    .respondedAt(LocalDateTime.now())
                    .build();
        } catch (Exception ex) {
            log.warn("AI tutor prompt failed for node {}: {}", node.getId(), ex.getMessage());
            return null;
        }
    }

    private NodeTutorPromptResponseDto generateFallbackTutorResponse(RoadmapNode node, String prompt) {
        String topic = firstNonBlank(node.getTitle(), "this topic");
        String objective = firstNonBlank(node.getObjective(), "build practical understanding");

        String answer = "For " + topic + ", focus on one concrete workflow at a time. "
                + "Your prompt was: \"" + compactText(prompt, 220) + "\". "
                + "Start by implementing a tiny example that satisfies: " + objective + ". "
                + "Then test edge cases and refactor for clarity.";

        return NodeTutorPromptResponseDto.builder()
                .nodeId(node.getId())
                .nodeTitle(node.getTitle())
                .prompt(prompt)
                .answer(answer)
                .keyTakeaways(List.of(
                        "Small practical examples accelerate retention.",
                        "Validate assumptions with tests or sample inputs.",
                        "Refactor after it works, not before."))
                .nextActions(List.of(
                        "Implement one mini example in less than 30 minutes.",
                        "Write down 2 mistakes you made and how to avoid them.",
                        "Run one self-check against the node objective."))
                .aiGenerated(false)
                .respondedAt(LocalDateTime.now())
                .build();
    }

    private NodeCourseContentDto generateAiNodeCourse(RoadmapNode node) {
        String topic = firstNonBlank(node.getTitle(), "Roadmap Topic").trim();
        String objective = firstNonBlank(node.getObjective(), "Apply the topic to real tasks").trim();
        String description = firstNonBlank(node.getDescription(), "").trim();
        String technologies = firstNonBlank(node.getTechnologies(), "").trim();
        String language = inferProjectLanguage(node);

        String systemPrompt = "You are a technical course author. "
                + "Create practical, short lessons in a W3Schools style: simple explanation, concrete example, quick checks. "
                + "Return only valid JSON with no markdown.";

        String userPrompt = String.format("""
                Create a compact node course.

                Topic: %s
                Objective: %s
                Description: %s
                Technologies: %s
                Difficulty: %s
                Preferred language for examples: %s

                Return ONLY JSON in this exact shape:
                {
                  "courseTitle": "string",
                  "intro": "string",
                  "lessons": [
                    {
                      "sectionTitle": "string",
                      "explanation": "string",
                      "miniExample": "string",
                      "codeSnippet": "string",
                      "commonPitfalls": ["string"],
                      "practiceTasks": ["string"]
                    }
                  ],
                  "checkpoints": [
                    {
                      "question": "string",
                      "answerHint": "string"
                    }
                  ],
                  "cheatSheet": ["string"],
                  "nextNodeFocus": "string"
                }

                Requirements:
                - 4 to 6 lessons
                - Keep explanations concise and practical
                - Add code snippets where relevant
                - Include 3 to 5 checkpoints
                - Include 5 to 9 cheat sheet bullets
                """, topic, objective, description, technologies, node.getDifficulty(), language);

        try {
            String aiResponse = aiClient.call(systemPrompt, userPrompt);
            String cleaned = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode payload = root.isObject() && root.has("course") ? root.get("course") : root;

            if (payload == null || !payload.isObject()) {
                return null;
            }

            String courseTitle = firstNonBlank(jsonText(payload, "courseTitle"), topic + " Practical Course").trim();
            String intro = firstNonBlank(jsonText(payload, "intro"),
                    "Learn " + topic + " step by step with practical examples.").trim();

            List<NodeCourseLessonDto> lessons = parseAiCourseLessons(payload.get("lessons"), topic, language);
            if (lessons.isEmpty()) {
                lessons = fallbackCourseLessons(topic, objective, language);
            }

            List<NodeCourseCheckpointDto> checkpoints = parseAiCourseCheckpoints(payload.get("checkpoints"), topic);
            if (checkpoints.isEmpty()) {
                checkpoints = fallbackCourseCheckpoints(topic);
            }

            List<String> cheatSheet = jsonStringList(payload, "cheatSheet");
            if (cheatSheet.isEmpty()) {
                cheatSheet = fallbackCheatSheet(topic, objective);
            }

            String nextNodeFocus = firstNonBlank(jsonText(payload, "nextNodeFocus"),
                    "Build one mini project to consolidate this node before moving on.");

            return NodeCourseContentDto.builder()
                    .nodeId(node.getId())
                    .nodeTitle(node.getTitle())
                    .courseTitle(courseTitle)
                    .intro(intro)
                    .difficulty(node.getDifficulty().name())
                    .technologies(node.getTechnologies())
                    .aiGenerated(true)
                    .generatedAt(LocalDateTime.now())
                    .lessons(lessons)
                    .checkpoints(checkpoints)
                    .cheatSheet(cheatSheet)
                    .nextNodeFocus(nextNodeFocus)
                    .build();
        } catch (Exception ex) {
            log.warn("AI node course generation failed for node {}: {}", node.getId(), ex.getMessage());
            return null;
        }
    }

    private NodeCourseContentDto generateFallbackNodeCourse(RoadmapNode node) {
        String topic = firstNonBlank(node.getTitle(), "Roadmap Topic").trim();
        String objective = firstNonBlank(node.getObjective(), "Apply the topic with practical tasks").trim();
        String language = inferProjectLanguage(node);

        return NodeCourseContentDto.builder()
                .nodeId(node.getId())
                .nodeTitle(node.getTitle())
                .courseTitle(topic + " Crash Course")
                .intro("A practical, W3Schools-style mini course to build confidence in " + topic + ".")
                .difficulty(node.getDifficulty().name())
                .technologies(node.getTechnologies())
                .aiGenerated(false)
                .generatedAt(LocalDateTime.now())
                .lessons(fallbackCourseLessons(topic, objective, language))
                .checkpoints(fallbackCourseCheckpoints(topic))
                .cheatSheet(fallbackCheatSheet(topic, objective))
                .nextNodeFocus("Use " + topic + " in a mini project and validate against acceptance checks.")
                .build();
    }

    private List<NodeCourseLessonDto> parseAiCourseLessons(JsonNode lessonsNode, String topic, String language) {
        if (lessonsNode == null || !lessonsNode.isArray()) {
            return List.of();
        }

        List<NodeCourseLessonDto> lessons = new ArrayList<>();
        int index = 1;
        for (JsonNode lessonNode : lessonsNode) {
            if (index > 6) {
                break;
            }

            String sectionTitle = firstNonBlank(jsonText(lessonNode, "sectionTitle"), "Lesson " + index).trim();
            String explanation = firstNonBlank(jsonText(lessonNode, "explanation"),
                    "Understand the core concept and apply it in practice.").trim();
            String miniExample = firstNonBlank(jsonText(lessonNode, "miniExample"),
                    "Apply " + topic + " in a small real-world scenario.").trim();
            String codeSnippet = jsonText(lessonNode, "codeSnippet");
            if (codeSnippet.isBlank()) {
                codeSnippet = buildCourseSnippet(language, sectionTitle);
            }

            List<String> commonPitfalls = jsonStringList(lessonNode, "commonPitfalls");
            if (commonPitfalls.isEmpty()) {
                commonPitfalls = List.of("Skipping validation and edge-case checks.");
            }

            List<String> practiceTasks = jsonStringList(lessonNode, "practiceTasks");
            if (practiceTasks.isEmpty()) {
                practiceTasks = List.of("Implement one focused exercise for this lesson.");
            }

            lessons.add(NodeCourseLessonDto.builder()
                    .sectionTitle(sectionTitle)
                    .explanation(explanation)
                    .miniExample(miniExample)
                    .codeSnippet(codeSnippet)
                    .commonPitfalls(commonPitfalls)
                    .practiceTasks(practiceTasks)
                    .build());
            index += 1;
        }

        return lessons;
    }

    private List<NodeCourseCheckpointDto> parseAiCourseCheckpoints(JsonNode checkpointsNode, String topic) {
        if (checkpointsNode == null || !checkpointsNode.isArray()) {
            return List.of();
        }

        List<NodeCourseCheckpointDto> checkpoints = new ArrayList<>();
        int index = 1;
        for (JsonNode checkpointNode : checkpointsNode) {
            if (index > 5) {
                break;
            }

            String question = firstNonBlank(jsonText(checkpointNode, "question"),
                    "Checkpoint " + index + " for " + topic).trim();
            String answerHint = firstNonBlank(jsonText(checkpointNode, "answerHint"),
                    "Use practical examples and explain your trade-offs.").trim();

            checkpoints.add(NodeCourseCheckpointDto.builder()
                    .question(question)
                    .answerHint(answerHint)
                    .build());
            index += 1;
        }

        return checkpoints;
    }

    private List<NodeCourseLessonDto> fallbackCourseLessons(String topic, String objective, String language) {
        return List.of(
                NodeCourseLessonDto.builder()
                        .sectionTitle("1. What this node solves")
                        .explanation("Understand where " + topic + " fits in real workflows and why teams use it.")
                        .miniExample("Map one product feature to the " + topic + " capability it needs.")
                        .codeSnippet(buildCourseSnippet(language, "intro"))
                        .commonPitfalls(List.of("Learning syntax without understanding business use cases."))
                        .practiceTasks(List.of("Write 3 bullet points describing where to apply " + topic + "."))
                        .build(),
                NodeCourseLessonDto.builder()
                        .sectionTitle("2. Core building blocks")
                        .explanation("Break the topic into small reusable concepts before coding.")
                        .miniExample("Implement one tiny unit that satisfies: " + objective)
                        .codeSnippet(buildCourseSnippet(language, "core-block"))
                        .commonPitfalls(List.of("Mixing too many concerns in a single module."))
                        .practiceTasks(List.of("Create one focused function/class and test it with sample data."))
                        .build(),
                NodeCourseLessonDto.builder()
                        .sectionTitle("3. Practical workflow")
                        .explanation("Use an iterative loop: design, implement, validate, and refine.")
                        .miniExample("Add validation and error handling to your first implementation.")
                        .codeSnippet(buildCourseSnippet(language, "workflow"))
                        .commonPitfalls(List.of("Skipping tests and assuming happy path behavior."))
                        .practiceTasks(List.of("Add one failing test, then fix the implementation."))
                        .build(),
                NodeCourseLessonDto.builder()
                        .sectionTitle("4. Production readiness")
                        .explanation("Polish naming, structure, and maintainability for long-term use.")
                        .miniExample("Refactor duplicated logic and document one key decision.")
                        .codeSnippet(buildCourseSnippet(language, "quality"))
                        .commonPitfalls(List.of("Optimizing too early before correctness is verified."))
                        .practiceTasks(List.of("Run a final checklist for readability, correctness, and edge cases."))
                        .build());
    }

    private List<NodeCourseCheckpointDto> fallbackCourseCheckpoints(String topic) {
        return List.of(
                NodeCourseCheckpointDto.builder()
                        .question("How would you explain " + topic + " to a teammate in 60 seconds?")
                        .answerHint("Describe purpose, one use case, and one limitation.")
                        .build(),
                NodeCourseCheckpointDto.builder()
                        .question("Which implementation decision improved reliability the most?")
                        .answerHint("Point to validation, tests, or error handling.")
                        .build(),
                NodeCourseCheckpointDto.builder()
                        .question("What would you refactor next in your current solution?")
                        .answerHint("Focus on clarity, separation of concerns, and maintainability.")
                        .build());
    }

    private List<String> fallbackCheatSheet(String topic, String objective) {
        return List.of(
                "Define success criteria before coding.",
                "Implement one small increment at a time.",
                "Validate edge cases early.",
                "Keep module boundaries clear.",
                "Document decisions and trade-offs.",
                "Objective reminder: " + objective,
                "Use " + topic + " in at least one practical mini-project.");
    }

    private String buildCourseSnippet(String language, String section) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String safeSection = compactText(firstNonBlank(section, "lesson"), 40);

        if (normalized.contains("java")) {
            return """
                    // %s snippet
                    public class LessonExample {
                        public static void main(String[] args) {
                            // TODO: apply concept for this lesson
                            System.out.println("Practice %s");
                        }
                    }
                    """.formatted(safeSection, safeSection);
        }

        if (normalized.contains("python")) {
            return """
                    # %s snippet
                    def run_example():
                        # TODO: apply concept for this lesson
                        return "Practice %s"

                    print(run_example())
                    """.formatted(safeSection, safeSection);
        }

        if (normalized.contains("typescript")) {
            return """
                    // %s snippet
                    function runExample(): string {
                      // TODO: apply concept for this lesson
                      return 'Practice %s';
                    }

                    console.log(runExample());
                    """.formatted(safeSection, safeSection);
        }

        return """
                // %s snippet
                function runExample() {
                  // TODO: apply concept for this lesson
                  return 'Practice %s';
                }

                console.log(runExample());
                """.formatted(safeSection, safeSection);
    }

    private NodeCourseContent persistNodeCourse(RoadmapNode node, Long userId, NodeCourseContentDto dto) {
        NodeCourseContent entity = NodeCourseContent.builder()
                .userId(userId)
                .node(node)
                .courseTitle(compactText(firstNonBlank(dto.getCourseTitle(), node.getTitle() + " Course"), 240))
                .intro(firstNonBlank(dto.getIntro(), ""))
                .lessonsJson(writeJsonValue(dto.getLessons()))
                .checkpointsJson(writeJsonValue(dto.getCheckpoints()))
                .cheatSheetJson(writeJsonValue(dto.getCheatSheet()))
                .nextNodeFocus(firstNonBlank(dto.getNextNodeFocus(), ""))
                .aiGenerated(dto.isAiGenerated())
                .generatedAt(dto.getGeneratedAt() != null ? dto.getGeneratedAt() : LocalDateTime.now())
                .build();

        return nodeCourseContentRepository.save(entity);
    }

    private NodeCourseContentDto toNodeCourseDto(NodeCourseContent entity) {
        return NodeCourseContentDto.builder()
                .historyId(entity.getId())
                .nodeId(entity.getNode().getId())
                .nodeTitle(entity.getNode().getTitle())
                .courseTitle(firstNonBlank(entity.getCourseTitle(), entity.getNode().getTitle() + " Course"))
                .intro(firstNonBlank(entity.getIntro(), ""))
                .difficulty(entity.getNode().getDifficulty().name())
                .technologies(entity.getNode().getTechnologies())
                .aiGenerated(entity.isAiGenerated())
                .generatedAt(entity.getGeneratedAt())
                .lessons(readCourseLessons(entity.getLessonsJson()))
                .checkpoints(readCourseCheckpoints(entity.getCheckpointsJson()))
                .cheatSheet(readStringList(entity.getCheatSheetJson()))
                .nextNodeFocus(firstNonBlank(entity.getNextNodeFocus(), ""))
                .build();
    }

    private String writeJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readStringList(String raw) {
        return readJson(raw, new TypeReference<List<String>>() {
        }, List.of());
    }

    private List<NodeCourseLessonDto> readCourseLessons(String raw) {
        return readJson(raw, new TypeReference<List<NodeCourseLessonDto>>() {
        }, List.of());
    }

    private List<NodeCourseCheckpointDto> readCourseCheckpoints(String raw) {
        return readJson(raw, new TypeReference<List<NodeCourseCheckpointDto>>() {
        }, List.of());
    }

    private <T> T readJson(String raw, TypeReference<T> typeReference, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return objectMapper.readValue(raw, typeReference);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String compactText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength));
    }

    private NodeType parseNodeType(Object rawType) {
        try {
            return NodeType.valueOf(normalizeEnum(rawType));
        } catch (Exception ex) {
            return NodeType.REQUIRED;
        }
    }

    private DifficultyLevel parseDifficulty(Object rawDifficulty) {
        try {
            return DifficultyLevel.valueOf(normalizeEnum(rawDifficulty));
        } catch (Exception ex) {
            return DifficultyLevel.BEGINNER;
        }
    }

    private EdgeType parseEdgeType(Object rawType) {
        try {
            return EdgeType.valueOf(normalizeEnum(rawType));
        } catch (Exception ex) {
            return EdgeType.REQUIRED;
        }
    }

    private String normalizeEnum(Object rawValue) {
        return rawValue == null ? "" : String.valueOf(rawValue).trim().toUpperCase();
    }
}
