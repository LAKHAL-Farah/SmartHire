package tn.esprit.msroadmap.DTO.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyBuddyChatRequestDto {
    private Long userId;
    private Long stepId;
    private String message;
}
