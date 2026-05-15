package tn.esprit.msroadmap.DTO.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeProjectValidationRequestDto {
    private String projectTitle;
    private String language;
    private String code;
    private List<String> acceptanceCriteria;
}
