package tn.esprit.msinterview.service;

import tn.esprit.msinterview.entity.ArchitectureDiagram;

import java.util.List;

public interface ArchitectureDiagramService {
    ArchitectureDiagram submitDiagram(Long answerId, Long sessionId, Long questionId, String diagramJson);
    ArchitectureDiagram explainDiagram(Long diagramId, Long sessionId, Long questionId, String explanation);
    ArchitectureDiagram getDiagramByAnswer(Long answerId);
    void evaluateDiagram(Long diagramId);
    List<ArchitectureDiagram> getDiagramsBySession(Long sessionId);
}
