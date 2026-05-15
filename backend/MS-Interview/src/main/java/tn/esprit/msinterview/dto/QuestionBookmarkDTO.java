package tn.esprit.msinterview.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBookmarkDTO {
    private Long id;
    private Long userId;
    private Long questionId;
    private String note;
    private String tagLabel;
    private LocalDateTime savedAt;
    
    // Light reference to question
    private InterviewQuestionDTO question;
}
