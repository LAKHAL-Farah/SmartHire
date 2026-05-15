package tn.esprit.msjob.service;

import tn.esprit.msjob.dto.JobCreateDTO;
import tn.esprit.msjob.dto.JobDTO;
import tn.esprit.msjob.entity.Job;
import tn.esprit.msjob.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateJob() {
        JobCreateDTO dto = new JobCreateDTO();
        dto.setTitle("Test Job");
        dto.setCompany("Test Company");
        dto.setLocationType("Remote");
        dto.setContractType("Full-time");
        dto.setExperienceLevel("Senior");
        dto.setDescription("Test description");

        Job job = new Job();
        job.setId(1L);
        job.setTitle("Test Job");

        when(jobRepository.save(any(Job.class))).thenReturn(job);

        JobDTO result = jobService.createJob(dto);

        assertNotNull(result);
        assertEquals("Test Job", result.getTitle());
        verify(jobRepository, times(1)).save(any(Job.class));
    }

    @Test
    void testGetAllJobs() {
        Job job1 = new Job();
        job1.setId(1L);
        job1.setTitle("Job 1");

        Job job2 = new Job();
        job2.setId(2L);
        job2.setTitle("Job 2");

        when(jobRepository.findAll()).thenReturn(Arrays.asList(job1, job2));

        List<JobDTO> result = jobService.getAllJobs();

        assertEquals(2, result.size());
        verify(jobRepository, times(1)).findAll();
    }

    @Test
    void testGetJobById() {
        Job job = new Job();
        job.setId(1L);
        job.setTitle("Test Job");

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        Optional<JobDTO> result = jobService.getJobById(1L);

        assertTrue(result.isPresent());
        assertEquals("Test Job", result.get().getTitle());
        verify(jobRepository, times(1)).findById(1L);
    }

    @Test
    void testUpdateJob() {
        Job existingJob = new Job();
        existingJob.setId(1L);
        existingJob.setTitle("Old Title");

        JobCreateDTO dto = new JobCreateDTO();
        dto.setTitle("New Title");

        when(jobRepository.findById(1L)).thenReturn(Optional.of(existingJob));
        when(jobRepository.save(any(Job.class))).thenReturn(existingJob);

        JobDTO result = jobService.updateJob(1L, dto);

        assertEquals("New Title", result.getTitle());
        verify(jobRepository, times(1)).findById(1L);
        verify(jobRepository, times(1)).save(existingJob);
    }

    @Test
    void testDeleteJob() {
        when(jobRepository.existsById(1L)).thenReturn(true);

        jobService.deleteJob(1L);

        verify(jobRepository, times(1)).existsById(1L);
        verify(jobRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteJobNotFound() {
        when(jobRepository.existsById(1L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> jobService.deleteJob(1L));
        verify(jobRepository, times(1)).existsById(1L);
        verify(jobRepository, never()).deleteById(1L);
    }
}
