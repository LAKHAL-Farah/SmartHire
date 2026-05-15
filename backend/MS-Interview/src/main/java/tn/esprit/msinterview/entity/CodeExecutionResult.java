package tn.esprit.msinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msinterview.entity.enumerated.CodeLanguage;

@Entity
@Table(name = "code_execution_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeExecutionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private SessionAnswer answer;

    @Enumerated(EnumType.STRING)
    private CodeLanguage language;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String sourceCode;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stderr;

    private String statusDescription;

    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private Long executionTimeMs;
    private Long memoryUsedKb;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String complexityNote;
}
