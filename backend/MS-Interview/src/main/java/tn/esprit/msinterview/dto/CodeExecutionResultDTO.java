package tn.esprit.msinterview.dto;

import lombok.*;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeExecutionResultDTO {
    private Long id;
    private Long answerId;
    private CodeLanguage language;
    private String sourceCode;
    private String stdout;
    private String stderr;
    private String statusDescription;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private Long executionTimeMs;
    private Long memoryUsedKb;
    private String complexityNote;
}
