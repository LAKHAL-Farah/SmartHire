package tn.esprit.msroadmap.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@ToString
public class MicroCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    @ToString.Exclude
    private RoadmapMilestone milestone;

    @Column(unique = true)
    private String certificateCode;

    private String pdfUrl;
    private String badgeUrl;

    @Enumerated(EnumType.STRING)
    private tn.esprit.msroadmap.Enums.CertificateStatus status = tn.esprit.msroadmap.Enums.CertificateStatus.GENERATED;

    @CreatedDate
    private LocalDateTime issuedAt;

    private boolean linkedInShared = false;
}
