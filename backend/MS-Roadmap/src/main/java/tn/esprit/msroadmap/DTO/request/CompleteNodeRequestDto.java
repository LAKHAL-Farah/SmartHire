package tn.esprit.msroadmap.DTO.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteNodeRequestDto {
    private Long userId;
    private Long nodeId;
}
