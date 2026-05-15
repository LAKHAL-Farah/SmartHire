package tn.esprit.msroadmap.DTO.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareResponseDto {
    private String shareToken;
    private String shareUrl;
    private boolean isPublic;
}
