package tn.esprit.msjob.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDTO {

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

    private Boolean verified;

    private String locationType;

    private String contractType;

    @Size(max = 50)
    private String salaryRange;

    private String experienceLevel;

    private List<String> skills;

    private String description;

    private LocalDateTime postedDate;

    // Placeholder for user MS
    private Long userId;
}
