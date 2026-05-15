package tn.esprit.msinterview.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;

import java.util.List;

@Entity
@Table(name = "interview_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long careerPathId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType roleType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficulty;

    private String domain;
    private String category;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String expectedPoints;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String followUps;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String hints;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String idealAnswer;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String sampleCode;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @com.fasterxml.jackson.annotation.JsonProperty("isActive")
    @com.fasterxml.jackson.annotation.JsonAlias("active")
    private boolean isActive;

    @JsonIgnore
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
    private List<SessionQuestionOrder> sessionQuestionOrders;

    @JsonIgnore
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
    private List<SessionAnswer> sessionAnswers;

    @JsonIgnore
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
    private List<QuestionBookmark> bookmarks;
}
