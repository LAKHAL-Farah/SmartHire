package tn.esprit.msroadmap.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "node_course_content")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeCourseContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    @ToString.Exclude
    private RoadmapNode node;

    private String courseTitle;

    @Column(columnDefinition = "TEXT")
    private String intro;

    @Column(columnDefinition = "TEXT")
    private String lessonsJson;

    @Column(columnDefinition = "TEXT")
    private String checkpointsJson;

    @Column(columnDefinition = "TEXT")
    private String cheatSheetJson;

    @Column(columnDefinition = "TEXT")
    private String nextNodeFocus;

    private boolean aiGenerated;

    private LocalDateTime generatedAt;
}
