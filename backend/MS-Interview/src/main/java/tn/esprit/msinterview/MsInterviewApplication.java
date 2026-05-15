package tn.esprit.msinterview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableAsync
@RestController
@Slf4j

public class MsInterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsInterviewApplication.class, args);
    }


    @GetMapping("/hello")
    public String hello() {
        return "Hello from Interview Service";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logFfmpegCheck() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg check: timed out while checking ffmpeg availability");
                return;
            }

            String output = new String(process.getInputStream().readAllBytes());
            String firstLine = output.lines().findFirst().orElse("ffmpeg output unavailable");
            log.info("ffmpeg check: {}", firstLine);
        } catch (Exception e) {
            log.warn("ffmpeg check: not available in PATH ({})", e.getMessage());
        }
    }

}
