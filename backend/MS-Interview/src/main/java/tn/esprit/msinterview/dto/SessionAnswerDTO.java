package tn.esprit.msinterview.dto;

import lombok.*;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAnswerDTO {
    private Long id;
    private Long sessionId;
    private Long questionId;
    private String answerText;
    private String codeAnswer;
    private String codeOutput;
    private CodeLanguage codeLanguage;
    private String videoUrl;
    private String audioUrl;
    private Integer retryCount;
    private Integer timeSpentSeconds;
    private LocalDateTime submittedAt;
    private boolean isFollowUp;
    private Long parentAnswerId;
    
    // Nested DTOs (one-to-one relations, but NOT session to avoid cycle)
    private AnswerEvaluationDTO answerEvaluation;
    private CodeExecutionResultDTO codeExecutionResult;
    private ArchitectureDiagramDTO architectureDiagram;
    private MLScenarioAnswerDTO mlScenarioAnswer;
}
