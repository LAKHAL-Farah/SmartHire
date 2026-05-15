package tn.esprit.msjob.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.msjob.entity.Job;
import tn.esprit.msjob.repository.JobRepository;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ResumeMatchService {

    private final NvidiaAiService nvidiaAiService;
    private final JobRepository jobRepository;

    public ResumeMatchService(NvidiaAiService nvidiaAiService, JobRepository jobRepository) {
        this.nvidiaAiService = nvidiaAiService;
        this.jobRepository = jobRepository;
    }

    public String matchResumeToJobs(MultipartFile file) throws IOException {
        String resumeText = extractText(file);
        log.info("Extracted {} characters from resume", resumeText.length());

        List<Job> jobs = jobRepository.findAll();

        if (jobs.isEmpty()) {
            return "There are no jobs available to match against your resume.";
        }

        String jobsContext = jobs.stream()
                .map(this::jobToText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
            You are a career advisor AI.
            A candidate has uploaded their resume. Analyze it and match it
            against the available job listings below.

            List the TOP 3 most suitable jobs for this candidate.
            For each job explain in 2-3 sentences WHY it matches their profile.
            Be specific about which skills or experiences align.

            === Candidate Resume ===
            %s
            === End of Resume ===

            === Available Jobs ===
            %s
            === End of Jobs ===
            """.formatted(resumeText, jobsContext);

        try {
            NvidiaAiService.AiResult result = nvidiaAiService.prompt(
                    "You are a career advisor AI.",
                    prompt
            );
            return result.content();
        } catch (RuntimeException ex) {
            log.warn("NVIDIA resume-match unavailable, using deterministic fallback: {}", ex.getMessage());
            return fallbackMatch(resumeText, jobs);
        }
    }

    private String fallbackMatch(String resumeText, List<Job> jobs) {
        String normalizedResume = Objects.requireNonNullElse(resumeText, "").toLowerCase(Locale.ROOT);

        List<ScoredJob> ranked = jobs.stream()
                .map(job -> new ScoredJob(job, scoreBySkills(normalizedResume, job)))
                .sorted(Comparator.comparingInt(ScoredJob::score).reversed())
                .limit(3)
                .toList();

        String intro = "AI provider is temporarily unavailable, so this is a deterministic skill-match ranking:";
        String details = ranked.stream()
                .map(scored -> formatScored(scored.job(), scored.score()))
                .collect(Collectors.joining("\n\n"));
        return intro + "\n\n" + details;
    }

    private int scoreBySkills(String resumeText, Job job) {
        if (job.getSkills() == null || job.getSkills().isEmpty()) {
            return 0;
        }

        int hits = 0;
        for (String skill : job.getSkills()) {
            if (skill != null && !skill.isBlank() && resumeText.contains(skill.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    private String formatScored(Job job, int score) {
        int total = job.getSkills() == null ? 0 : job.getSkills().size();
        int pct = total == 0 ? 0 : Math.round((score * 100f) / total);
        return String.format(
                "- %s at %s\n  Match score: %d%% (%d/%d listed skills found in resume)\n  Skills: %s",
                safe(job.getTitle()),
                safe(job.getCompany()),
                pct,
                score,
                total,
                String.join(", ", job.getSkills() == null ? List.of() : job.getSkills())
        );
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "N/A");
    }

    private record ScoredJob(Job job, int score) {
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String jobToText(Job job) {
        return String.format(
                "ID: %d | Title: %s | Company: %s | Location: %s | " +
                        "Contract: %s | Salary: %s | Level: %s | Skills: %s",
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocationType(),
                job.getContractType(),
                job.getSalaryRange(),
                job.getExperienceLevel(),
                String.join(", ", job.getSkills())
        );
    }
}