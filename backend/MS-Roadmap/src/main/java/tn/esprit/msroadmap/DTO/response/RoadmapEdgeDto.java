package tn.esprit.msroadmap.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprit.msroadmap.Enums.EdgeType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapEdgeDto {
    private Long id;
    private String fromNodeId;
    private String toNodeId;
    private EdgeType type;
}
