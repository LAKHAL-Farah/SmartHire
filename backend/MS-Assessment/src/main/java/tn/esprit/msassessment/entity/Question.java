package tn.esprit.msassessment.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msassessment.entity.enums.DifficultyLevel;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "msa_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private QuestionCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    /** Points awarded when the candidate picks the correct choice. */
    @Column(nullable = false)
    @Builder.Default
    private Integer points = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private DifficultyLevel difficulty = DifficultyLevel.MEDIUM;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Tag for topic-based quizzes (e.g. "java", "sql"). Used with random selection by keyword.
     */
    @Column(length = 128)
    private String topic;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<AnswerChoice> choices = new ArrayList<>();
}
