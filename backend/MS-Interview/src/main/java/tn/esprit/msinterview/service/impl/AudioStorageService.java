package tn.esprit.msinterview.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class AudioStorageService {

    @Value("${whisper.temp.dir}")
    private String tempDir;

    /**
     * Saves uploaded audio MultipartFile to disk.
     * Returns the absolute path to the saved file.
     */
    public String saveAudioFile(MultipartFile audioFile) throws IOException {
        Path baseDir = Paths.get(tempDir).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);

        // Generate unique filename
        String filename = "audio_" + UUID.randomUUID() + ".webm";
        Path destination = baseDir.resolve(filename);

        audioFile.transferTo(destination.toFile());
        log.info("Audio saved to: {}", destination.toAbsolutePath());

        return destination.toAbsolutePath().toString();
    }
}