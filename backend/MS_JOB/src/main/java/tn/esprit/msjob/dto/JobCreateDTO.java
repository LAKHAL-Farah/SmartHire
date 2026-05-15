package tn.esprit.msjob.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCreateDTO {

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

    private Boolean verified = false;

    @NotBlank
    private String locationType;

    @NotBlank
    private String contractType;

    @Size(max = 50)
    private String salaryRange;

    @NotBlank
    private String experienceLevel;

    private List<String> skills;

    @NotBlank
    private String description;

    // Placeholder for user MS
    private Long userId;
}
