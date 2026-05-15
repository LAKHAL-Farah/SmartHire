package tn.esprit.msroadmap.ServicesImpl;

import tn.esprit.msroadmap.DTO.request.ReplanRequestDto;
import tn.esprit.msroadmap.DTO.request.RoadmapGenerationRequestDto;
import tn.esprit.msroadmap.DTO.request.NodeProjectValidationRequestDto;
import tn.esprit.msroadmap.DTO.request.NodeTutorPromptRequestDto;
import tn.esprit.msroadmap.DTO.response.NodeCourseContentDto;
import tn.esprit.msroadmap.DTO.response.NodeProjectLabDto;
import tn.esprit.msroadmap.DTO.response.NodeQuizResponseDto;
import tn.esprit.msroadmap.DTO.response.NodeProjectValidationResponseDto;
import tn.esprit.msroadmap.DTO.response.NodeTutorPromptResponseDto;
import tn.esprit.msroadmap.DTO.response.RoadmapVisualResponse;

import java.util.List;

public interface IRoadmapVisualService {
    RoadmapVisualResponse generateVisualRoadmap(RoadmapGenerationRequestDto request);
    RoadmapVisualResponse generateVisualRoadmapWithNemotron(RoadmapGenerationRequestDto request);
    RoadmapVisualResponse getRoadmapGraph(Long roadmapId);
    NodeQuizResponseDto generateNodeQuiz(Long nodeId, Long userId, Integer questionCount);
    NodeProjectLabDto generateNodeProjectLab(Long nodeId, Long userId);
    List<NodeProjectLabDto> getNodeProjectLabHistory(Long nodeId, Long userId);
    NodeProjectValidationResponseDto validateNodeProjectSubmission(Long nodeId, Long userId, NodeProjectValidationRequestDto request);
    NodeTutorPromptResponseDto askNodeTutor(Long nodeId, Long userId, NodeTutorPromptRequestDto request);
    NodeCourseContentDto generateNodeCourse(Long nodeId, Long userId, boolean refresh);
    List<NodeCourseContentDto> getNodeCourseHistory(Long nodeId, Long userId);
    RoadmapVisualResponse startNode(Long nodeId, Long userId);
    RoadmapVisualResponse completeNode(Long nodeId, Long userId);
    RoadmapVisualResponse replanVisualRoadmap(Long roadmapId, ReplanRequestDto request);
}
