package tn.esprit.msjob.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprit.msjob.entity.ApplicationStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplicationDTO {

    private Long id;

    private Long userId;

    private Long jobId;

    private String resumeUrl;

    private ApplicationStatus status;

    private LocalDateTime appliedAt;
}

