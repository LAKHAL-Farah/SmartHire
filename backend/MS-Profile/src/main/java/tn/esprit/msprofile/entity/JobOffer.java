package tn.esprit.msprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "job_offer")
@Getter
@Setter
@NoArgsConstructor
public class JobOffer extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 255)
    private String company;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String rawDescription;

    @Lob
    private String extractedKeywords;

    @Column(length = 512)
    private String sourceUrl;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "jobOffer", fetch = LAZY)
    private List<CVVersion> versions = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

