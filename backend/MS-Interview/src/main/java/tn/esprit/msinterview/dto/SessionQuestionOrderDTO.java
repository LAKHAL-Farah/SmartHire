package tn.esprit.msinterview.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionQuestionOrderDTO {
    private Long id;
    private Long sessionId;
    private Long questionId;
    private Integer questionOrder;
    private Long nextQuestionId;
    private Integer timeAllottedSeconds;
    private boolean wasSkipped;
    
    // Light reference to avoid circular dependency
    private InterviewQuestionDTO question;
}
