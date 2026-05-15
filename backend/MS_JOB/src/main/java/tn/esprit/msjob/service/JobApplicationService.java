package tn.esprit.msjob.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msjob.dto.JobApplicationDTO;
import tn.esprit.msjob.entity.ApplicationStatus;
import tn.esprit.msjob.entity.Job;
import tn.esprit.msjob.entity.JobApplication;
import tn.esprit.msjob.mapper.JobApplicationMapper;
import tn.esprit.msjob.repository.JobApplicationRepository;
import tn.esprit.msjob.repository.JobRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final ResumeStorageService resumeStorageService;
    private final JobApplicationMapper jobApplicationMapper;

    public JobApplicationService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            ResumeStorageService resumeStorageService,
            JobApplicationMapper jobApplicationMapper
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.resumeStorageService = resumeStorageService;
        this.jobApplicationMapper = jobApplicationMapper;
    }

    public JobApplicationDTO applyToJob(Long jobId, Long userId, MultipartFile resumeFile) {
        if (jobId == null) throw new IllegalArgumentException("jobId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        if (jobApplicationRepository.existsByJob_IdAndUserId(jobId, userId)) {
            throw new DataIntegrityViolationException("You already applied to this job");
        }

        String resumeUrl = resumeStorageService.storeResume(resumeFile);

        JobApplication application = JobApplication.builder()
                .job(job)
                .userId(userId)
                .resumeUrl(resumeUrl)
                .status(ApplicationStatus.PENDING)
                .appliedAt(LocalDateTime.now())
                .build();

        try {
            JobApplication saved = jobApplicationRepository.save(application);
            return jobApplicationMapper.toDTO(saved);
        } catch (DataIntegrityViolationException e) {
            // Covers race condition if two requests slip past exists() check.
            throw new DataIntegrityViolationException("You already applied to this job", e);
        }
    }

    @Transactional(readOnly = true)
    public List<JobApplicationDTO> getApplicationsByJobId(Long jobId) {
        return jobApplicationMapper.toDTOList(jobApplicationRepository.findByJob_IdOrderByAppliedAtDesc(jobId));
    }

    @Transactional(readOnly = true)
    public List<JobApplicationDTO> getApplicationsByUserId(Long userId) {
        return jobApplicationMapper.toDTOList(jobApplicationRepository.findByUserIdOrderByAppliedAtDesc(userId));
    }

    public JobApplicationDTO updateStatus(Long applicationId, ApplicationStatus status) {
        if (status == null) throw new IllegalArgumentException("status is required");

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found with id: " + applicationId));

        application.setStatus(status);
        JobApplication saved = jobApplicationRepository.save(application);
        return jobApplicationMapper.toDTO(saved);
    }

    public void deleteApplication(Long applicationId) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new RuntimeException("Application not found with id: " + applicationId);
        }
        jobApplicationRepository.deleteById(applicationId);
    }
}

