package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewDto {
    private Integer score;
    private Integer readmeScore;
    private Integer structureScore;
    private Integer testScore;
    private Integer ciScore;
    private String feedback;
    private List<String> recommendations;
}
