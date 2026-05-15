package tn.esprit.msjob.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msjob.dto.JobCreateDTO;
import tn.esprit.msjob.dto.JobDTO;
import tn.esprit.msjob.entity.Job;
import tn.esprit.msjob.mapper.JobMapper;
import tn.esprit.msjob.repository.JobRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class JobService {

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    public JobService(JobRepository jobRepository, JobMapper jobMapper) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
    }

    public JobDTO createJob(JobCreateDTO jobCreateDTO) {
        Job job = jobMapper.toEntity(jobCreateDTO);
        job.setPostedDate(LocalDateTime.now());
        Job savedJob = jobRepository.save(job);
        return jobMapper.toDTO(savedJob);
    }

    public List<JobDTO> getAllJobs() {
        List<Job> jobs = jobRepository.findAll();
        return jobMapper.toDTOList(jobs);
    }

    public Optional<JobDTO> getJobById(Long id) {
        Optional<Job> job = jobRepository.findById(id);
        return job.map(jobMapper::toDTO);
    }

    public JobDTO updateJob(Long id, JobCreateDTO jobCreateDTO) {
        Optional<Job> existingJobOpt = jobRepository.findById(id);
        if (existingJobOpt.isPresent()) {
            Job existingJob = existingJobOpt.get();
            jobMapper.updateEntityFromDTO(jobCreateDTO, existingJob);
            Job updatedJob = jobRepository.save(existingJob);
            return jobMapper.toDTO(updatedJob);
        } else {
            throw new RuntimeException("Job not found with id: " + id);
        }
    }

    public void deleteJob(Long id) {
        if (jobRepository.existsById(id)) {
            jobRepository.deleteById(id);
        } else {
            throw new RuntimeException("Job not found with id: " + id);
        }
    }
}
