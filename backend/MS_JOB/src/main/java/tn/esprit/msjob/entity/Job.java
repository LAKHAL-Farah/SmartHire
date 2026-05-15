package tn.esprit.msjob.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 255)
    private String company;

    @Size(max = 10)
    private String companyInitials;

    @Size(max = 7)
    private String companyColor;

    @Builder.Default
    private Boolean verified = false;

    private String locationType;

    private String contractType;

    @Size(max = 50)
    private String salaryRange;

    private String experienceLevel;

    @ElementCollection
    @CollectionTable(name = "job_skills", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "skill")
    private List<String> skills;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime postedDate;

    // Placeholder for user MS integration
    private Long userId;
}
