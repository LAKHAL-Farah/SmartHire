package tn.esprit.msinterview.dto;

import lombok.*;
import tn.esprit.msinterview.entity.enumerated.DifficultyLevel;
import tn.esprit.msinterview.entity.enumerated.QuestionType;
import tn.esprit.msinterview.entity.enumerated.RoleType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewQuestionDTO {
    private Long id;
    private Long careerPathId;
    private RoleType roleType;
    private String questionText;
    private QuestionType type;
    private DifficultyLevel difficulty;
    private String domain;
    private String category;
    private String expectedPoints;
    private String followUps;
    private String hints;
    private String idealAnswer;
    private String sampleCode;
    private String tags;
    private String metadata;
    private String ttsAudioUrl;
    private boolean isActive;
}
