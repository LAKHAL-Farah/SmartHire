package tn.esprit.msroadmap.DTO.response;

import lombok.*;
import java.time.LocalDateTime;
import tn.esprit.msroadmap.Enums.CertificateStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDto {
    private Long id;
    private Long userId;
    private Long milestoneId;
    private String certificateCode;
    private String pdfUrl;
    private String badgeUrl;
    private CertificateStatus status;
    private LocalDateTime issuedAt;
    private boolean linkedInShared;
}
