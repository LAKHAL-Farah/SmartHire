package tn.esprit.msjob.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.msjob.entity.Job;
import tn.esprit.msjob.repository.JobRepository;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiChatService {

    private final NvidiaAiService nvidiaAiService;
    private final JobRepository jobRepository;

    public AiChatService(NvidiaAiService nvidiaAiService, JobRepository jobRepository) {
        this.nvidiaAiService = nvidiaAiService;
        this.jobRepository = jobRepository;
    }

    public String chat(String userMessage) {
        List<Job> jobs = jobRepository.findAll();

        if (jobs.isEmpty()) {
            return "There are no jobs available in the database right now.";
        }

        String jobsContext = jobs.stream()
                .map(this::jobToText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
            You are a helpful job search assistant.
            You have access to the following job listings from our platform.
            Answer the user's question using ONLY these jobs.
            If no jobs match, say so clearly.
            Format each matching job with its title, company, location, salary and required skills.
            Be concise and friendly.

            === Available Jobs ===
            %s
            === End of Jobs ===

            User question: %s
            """.formatted(jobsContext, userMessage);

        log.info("Sending prompt to NVIDIA NIM for message: {}", userMessage);

        try {
            NvidiaAiService.AiResult result = nvidiaAiService.prompt(
                    "You are a helpful job search assistant.",
                    prompt
            );
            return result.content();
        } catch (RuntimeException ex) {
            log.warn("NVIDIA chat unavailable, using deterministic fallback: {}", ex.getMessage());
            return fallbackAnswer(userMessage, jobs);
        }
    }

    private String fallbackAnswer(String userMessage, List<Job> jobs) {
        List<String> keywords = Arrays.stream(userMessage.toLowerCase(Locale.ROOT).split("[^a-z0-9+.#-]+"))
                .filter(token -> token.length() >= 3)
                .distinct()
                .toList();

        List<Job> ranked = jobs.stream()
                .sorted(Comparator.comparingInt((Job job) -> score(job, keywords)).reversed())
                .limit(3)
                .toList();

        String header = "AI provider is temporarily unavailable, so here are the best matches from local filtering:";
        String body = ranked.stream()
                .map(this::formatJobCard)
                .collect(Collectors.joining("\n\n"));

        return header + "\n\n" + body;
    }

    private int score(Job job, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 1;
        }

        String haystack = String.join(" ",
                        safe(job.getTitle()),
                        safe(job.getCompany()),
                        safe(job.getDescription()),
                        safe(job.getExperienceLevel()),
                        safe(job.getContractType()),
                        safe(job.getLocationType()),
                        String.join(" ", job.getSkills() == null ? List.of() : job.getSkills())
                )
                .toLowerCase(Locale.ROOT);

        int total = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword)) {
                total++;
            }
        }
        return total;
    }

    private String formatJobCard(Job job) {
        return String.format(
                "- %s at %s\n  Location: %s | Contract: %s | Salary: %s\n  Skills: %s",
                safe(job.getTitle()),
                safe(job.getCompany()),
                safe(job.getLocationType()),
                safe(job.getContractType()),
                safe(job.getSalaryRange()),
                String.join(", ", job.getSkills() == null ? List.of() : job.getSkills())
        );
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "N/A");
    }

    private String jobToText(Job job) {
        return String.format(
                "ID: %d | Title: %s | Company: %s | Location: %s | " +
                        "Contract: %s | Salary: %s | Level: %s | Skills: %s\nDescription: %s",
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocationType(),
                job.getContractType(),
                job.getSalaryRange(),
                job.getExperienceLevel(),
                String.join(", ", job.getSkills()),
                job.getDescription()
        );
    }
}