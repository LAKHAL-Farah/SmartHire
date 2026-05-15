package tn.esprit.msroadmap.Services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.msroadmap.Entities.Roadmap;
import tn.esprit.msroadmap.Entities.RoadmapEdge;
import tn.esprit.msroadmap.Entities.RoadmapNode;
import tn.esprit.msroadmap.Entities.RoadmapReplanEvent;
import tn.esprit.msroadmap.Exception.ResourceNotFoundException;
import tn.esprit.msroadmap.Repositories.RoadmapEdgeRepository;
import tn.esprit.msroadmap.Repositories.RoadmapNodeRepository;
import tn.esprit.msroadmap.Repositories.RoadmapReplanEventRepository;
import tn.esprit.msroadmap.Repositories.RoadmapRepository;
import tn.esprit.msroadmap.ServicesImpl.IRoadmapReplanService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class RoadmapReplanServiceImpl implements IRoadmapReplanService {

    private final RoadmapReplanEventRepository repository;
    private final RoadmapRepository roadmapRepository;
    private final RoadmapNodeRepository nodeRepository;
    private final RoadmapEdgeRepository edgeRepository;
    private final ObjectMapper objectMapper;

    @Override
    public RoadmapReplanEvent replanRoadmap(Long roadmapId, String reason, String newPlan) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElseThrow(() -> new ResourceNotFoundException("Roadmap not found"));

        List<RoadmapNode> nodes = nodeRepository.findByRoadmapIdOrderByStepOrderAsc(roadmapId);
        List<RoadmapEdge> edges = edgeRepository.findByRoadmapId(roadmapId);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roadmapId", roadmap.getId());
        snapshot.put("title", roadmap.getTitle());
        snapshot.put("status", roadmap.getStatus() != null ? roadmap.getStatus().name() : null);
        snapshot.put("completedSteps", roadmap.getCompletedSteps());
        snapshot.put("totalSteps", roadmap.getTotalSteps());

        List<Map<String, Object>> nodeDtos = new ArrayList<>();
        for (RoadmapNode node : nodes) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", node.getId());
            n.put("nodeId", node.getNodeId());
            n.put("title", node.getTitle());
            n.put("stepOrder", node.getStepOrder());
            n.put("status", node.getStatus() != null ? node.getStatus().name() : null);
            nodeDtos.add(n);
        }

        List<Map<String, Object>> edgeDtos = new ArrayList<>();
        for (RoadmapEdge edge : edges) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", edge.getId());
            e.put("fromNodeId", edge.getFromNodeId());
            e.put("toNodeId", edge.getToNodeId());
            e.put("type", edge.getType() != null ? edge.getType().name() : null);
            edgeDtos.add(e);
        }

        snapshot.put("nodes", nodeDtos);
        snapshot.put("edges", edgeDtos);

        RoadmapReplanEvent e = new RoadmapReplanEvent();
        e.setRoadmap(roadmap);
        e.setTriggerStepId(null);
        e.setReason(reason);
        e.setPreviousPlan(toJson(snapshot));
        e.setNewPlan(newPlan);
        e.setReplannedAt(LocalDateTime.now());
        return repository.save(e);
    }

    @Override
    public List<RoadmapReplanEvent> getReplanHistory(Long roadmapId) {
        return repository.findByRoadmapIdOrderByReplannedAtDesc(roadmapId);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
