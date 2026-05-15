package tn.esprit.msinterview.controller;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tn.esprit.msinterview.ai.TTSClient;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

@RestController
@RequestMapping({"/audio", "/api/v1/audio"})
@RequiredArgsConstructor
@Slf4j
public class AudioController {

    @Value("${smarthire.audio.temp-dir:#{systemProperties['java.io.tmpdir']}}")
    private String tempDir;

    @Value("${smarthire.audio.base-url:/interview-service/api/v1/audio}")
    private String audioBaseUrl;

    private final TTSClient ttsClient;

    @PostMapping("/tts/speak")
    public ResponseEntity<Map<String, String>> speakText(
            @RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String filename = ttsClient.synthesize(text);
        if (filename == null) {
            return ResponseEntity.internalServerError().build();
        }

        Path filePath = ttsClient.resolveAudioFilePath(filename);

        return ResponseEntity.ok(Map.of(
                "audioUrl", buildAudioUrl(filename),
                "filename", filename,
                "filePath", filePath.toAbsolutePath().toString()
        ));
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> serveAudio(@PathVariable String filename) {

        if (!filename.matches("[a-zA-Z0-9_\\-]+\\.(wav|mp3|webm|ogg)")) {
            log.warn("[AudioController] Rejected unsafe filename: {}", filename);
            return ResponseEntity.badRequest().build();
        }

        Path baseDir = Paths.get(tempDir).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(filename).normalize();

        try {
            if (!filePath.startsWith(baseDir)) {
                log.warn("[AudioController] Path traversal attempt blocked: {}", filename);
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath)) {
                Path fallback = ttsClient.resolveAudioFilePath(filename);
                if (Files.exists(fallback)) {
                    filePath = fallback;
                }
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.error("[AudioController] File not found or not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Detect content type by extension
            MediaType contentType;
            if (filename.endsWith(".mp3")) {
                contentType = MediaType.parseMediaType("audio/mpeg");
            } else if (filename.endsWith(".wav")) {
                contentType = MediaType.parseMediaType("audio/wav");
            } else if (filename.endsWith(".webm")) {
                contentType = MediaType.parseMediaType("audio/webm");
            } else {
                contentType = MediaType.parseMediaType("audio/ogg");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentLength(resource.contentLength());
            headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(30)));
            headers.set("Accept-Ranges", "bytes");

            log.info("[AudioController] Serving: {} ({} bytes, {})", 
                filename, resource.contentLength(), contentType);

            return ResponseEntity.ok().headers(headers).body(resource);

        } catch (Exception e) {
            log.error("[AudioController] Error serving {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/test-tts")
    public ResponseEntity<Map<String, String>> testTts(
            @RequestParam(defaultValue = "Hello. Welcome to SmartHire AI.") String text) {

        log.info("[AudioController] /test-tts called with text: \"{}\"", text);
        try {
            String filename = ttsClient.synthesize(text);
            if (filename == null || filename.isBlank()) {
                return ResponseEntity.internalServerError().body(Map.of("error", "TTS synthesis failed"));
            }

            Path filePath = ttsClient.resolveAudioFilePath(filename);
            String url = buildAudioUrl(filename);

            log.info("[AudioController] TTS test OK -> {}", url);
            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "filePath", filePath.toAbsolutePath().toString(),
                    "filename", filename,
                    "text", text
            ));
        } catch (Exception e) {
            log.error("[AudioController] TTS test FAILED: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // Frontend calls this after audio plays to clean up disk
    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteAudio(@PathVariable String filename) {
        ttsClient.deleteFile(filename);
        return ResponseEntity.noContent().build();
    }

    private String buildAudioUrl(String filename) {
        String base = audioBaseUrl == null || audioBaseUrl.isBlank()
                ? "/interview-service/api/v1/audio"
                : audioBaseUrl.trim();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + "/" + filename;
    }
}