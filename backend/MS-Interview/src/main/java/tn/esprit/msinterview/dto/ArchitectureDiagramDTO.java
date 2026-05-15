package tn.esprit.msinterview.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchitectureDiagramDTO {
    private Long id;
    private Long answerId;
    private String diagramJson;
    private Integer nodeCount;
    private Integer edgeCount;
    private String componentTypes;
    private Double designScore;
    private Double aiDesignScore;
    private String requirementsMet;
    private String requirementsMissed;
    private String aiFeedback;
    private String followUpGenerated;
    private LocalDateTime evaluatedAt;
}
