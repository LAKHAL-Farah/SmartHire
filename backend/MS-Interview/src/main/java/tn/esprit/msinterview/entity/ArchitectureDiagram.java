package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "architecture_diagrams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchitectureDiagram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private SessionAnswer answer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String diagramJson;

    private Integer nodeCount;
    private Integer edgeCount;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String componentTypes;

    private Double designScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String requirementsMet;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String requirementsMissed;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String followUpGenerated;

    private LocalDateTime evaluatedAt;
}
