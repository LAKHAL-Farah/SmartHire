package tn.esprit.msjob;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import tn.esprit.msjob.entity.ApplicationStatus;
import tn.esprit.msjob.entity.Job;
import tn.esprit.msjob.entity.JobApplication;
import tn.esprit.msjob.repository.JobApplicationRepository;
import tn.esprit.msjob.repository.JobRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class JobApplicationRepositoryTest {

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobApplicationRepository jobApplicationRepository;

    @Test
    void enforcesSingleApplicationPerJobAndUser() {
        Job job = Job.builder()
                .title("Backend")
                .company("ACME")
                .build();
        job = jobRepository.save(job);

        JobApplication a1 = JobApplication.builder()
                .job(job)
                .userId(1L)
                .resumeUrl("/uploads/resumes/a.pdf")
                .status(ApplicationStatus.PENDING)
                .appliedAt(LocalDateTime.now())
                .build();
        jobApplicationRepository.saveAndFlush(a1);

        assertTrue(jobApplicationRepository.existsByJob_IdAndUserId(job.getId(), 1L));
    }
}

