package tn.esprit.msinterview.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component
@Slf4j
public class TTSClient {

    // ── ElevenLabs (Primary) ────────────────────────────────
    @Value("${elevenlabs.api.key}")
    private String elevenLabsApiKey;

    @Value("${elevenlabs.api.base-url}")
    private String elevenLabsBaseUrl;

    @Value("${elevenlabs.voice.id}")
    private String voiceId;

    @Value("${elevenlabs.model.id:eleven_turbo_v2_5}")
    private String modelId;

    @Value("${elevenlabs.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${elevenlabs.timeout.read:15000}")
    private int readTimeout;

    // ── Kokoro (Fallback) ───────────────────────────────────
    @Value("${kokoro.python.executable:python}")
    private String pythonExecutable;

    @Value("${kokoro.script.path:}")
    private String kokoroScriptPath;

    @Value("${kokoro.base-url:}")
    private String kokoroBaseUrl;

    @Value("${kokoro.audio.dir}")
    private String audioDir;

    @Value("${smarthire.audio.base-url:/interview-service/api/v1/audio}")
    private String audioBaseUrl;

    private Path resolvedAudioDirPath;
    private RestTemplate elevenLabsRestTemplate;
    private Path resolvedKokoroScriptPath;
    private String resolvedPythonExecutable;
    private String resolvedKokoroBaseUrl;

    private final Map<String, String> generatedBySessionQuestion = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        resolvedAudioDirPath = Paths.get(audioDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(resolvedAudioDirPath);
            log.info("TTS audio directory ready: {}", resolvedAudioDirPath);
        } catch (IOException e) {
            log.error("Could not create TTS audio dir: {}", e.getMessage());
        }

        // Build RestTemplate with tight timeouts for ElevenLabs
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.elevenLabsRestTemplate = new RestTemplate(factory);

        resolvedKokoroBaseUrl = normalizeBaseUrl(kokoroBaseUrl);
        resolvedKokoroScriptPath = resolveConfiguredPath(kokoroScriptPath);
        resolvedPythonExecutable = resolveExecutable(pythonExecutable);

        if ((resolvedKokoroBaseUrl == null || resolvedKokoroBaseUrl.isBlank())
            && resolvedKokoroScriptPath != null
            && !Files.exists(resolvedKokoroScriptPath)) {
            log.warn("Kokoro script configured but not found: {}", resolvedKokoroScriptPath);
        }

        log.info("TTSClient initialized: primary=ElevenLabs model={} voice={} fallback=Kokoro",
            modelId, voiceId);
    }

    /**
     * Main entry point: try ElevenLabs first, fall back to Kokoro on failure.
     * Returns filename of saved audio (MP3 or WAV).
     * Returns null if BOTH fail — caller must handle gracefully.
     */
    public String synthesize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String ttsText = toTtsText(text);
        log.info("TTSClient.synthesize() — {} chars via ElevenLabs", ttsText.length());

        // TRY ELEVENLABS FIRST
        String result = synthesizeElevenLabs(ttsText);
        if (result != null) {
            log.debug("TTSClient: ElevenLabs success → {}", result);
            return result;
        }

        // FALLBACK TO KOKORO
        log.warn("TTSClient: ElevenLabs failed — falling back to Kokoro");
        result = synthesizeKokoro(ttsText);
        if (result != null) {
            log.debug("TTSClient: Kokoro fallback success → {}", result);
            return result;
        }

        log.error("TTSClient: BOTH ElevenLabs and Kokoro failed for text: {}",
            ttsText.substring(0, Math.min(50, ttsText.length())));
        return null;
    }

    // ─── ELEVENLABS IMPLEMENTATION ─────────────────────────

    private String synthesizeElevenLabs(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("xi-api-key", elevenLabsApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "audio/mpeg");

            Map<String, Object> voiceSettings = Map.of(
                "stability",        0.45,
                "similarity_boost", 0.80,
                "style",            0.20,
                "use_speaker_boost",true
            );

            Map<String, Object> body = Map.of(
                "text",           text,
                "model_id",       modelId,
                "voice_settings", voiceSettings
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = elevenLabsRestTemplate.postForEntity(
                elevenLabsBaseUrl + "/text-to-speech/" + voiceId,
                request,
                byte[].class
            );

            if (response.getStatusCode() != HttpStatus.OK
                    || response.getBody() == null
                    || response.getBody().length == 0) {
                log.warn("ElevenLabs returned: {}", response.getStatusCode());
                return null;
            }

            // Save as MP3
            String filename = "tts_" + UUID.randomUUID() + ".mp3";
            Path filePath = resolvedAudioDirPath.resolve(filename);
            Files.write(filePath, response.getBody());

            log.info("ElevenLabs TTS saved: {} ({} bytes)",
                filename, response.getBody().length);
            return filename;

        } catch (Exception e) {
            log.warn("ElevenLabs TTS failed: {}", e.getMessage());
            return null;
        }
    }

    // ─── KOKORO FALLBACK IMPLEMENTATION ────────────────────

    private String synthesizeKokoro(String text) {
        if (resolvedKokoroBaseUrl != null && !resolvedKokoroBaseUrl.isBlank()) {
            return synthesizeKokoroRemote(text);
        }

        if (resolvedKokoroScriptPath == null) {
            log.warn("Kokoro fallback disabled — kokoro.script.path not set");
            return null;
        }

        if (!Files.exists(resolvedKokoroScriptPath)) {
            log.error("Kokoro fallback script missing: {}", resolvedKokoroScriptPath);
            return null;
        }

        try {
            String filename = "tts_" + UUID.randomUUID() + ".wav";
            String outputPath = resolvedAudioDirPath.resolve(filename).toString();

            ProcessBuilder pb = new ProcessBuilder(
                resolvedPythonExecutable, resolvedKokoroScriptPath.toString(), text, outputPath
            );
            pb.redirectErrorStream(false);
            Path workingDir = resolvedKokoroScriptPath.getParent();
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }

            Process process = pb.start();
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("Kokoro fallback timed out");
                return null;
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (process.exitValue() != 0 || !stdout.equals("OK")) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                log.error("Kokoro fallback failed: {}", stderr);
                return null;
            }

            if (!Files.exists(Paths.get(outputPath))) {
                log.error("Kokoro produced no output file");
                return null;
            }

            log.info("Kokoro fallback saved: {} ({} bytes)",
                filename, Files.size(Paths.get(outputPath)));
            return filename;

        } catch (Exception e) {
            log.error("Kokoro fallback error: {}", e.getMessage());
            return null;
        }
    }

    private String synthesizeKokoroRemote(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of("text", text);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = elevenLabsRestTemplate.postForEntity(
                resolvedKokoroBaseUrl + "/tts",
                request,
                byte[].class
            );

            if (response.getStatusCode() != HttpStatus.OK
                    || response.getBody() == null
                    || response.getBody().length == 0) {
                log.warn("Kokoro HTTP returned: {}", response.getStatusCode());
                return null;
            }

            String filename = "tts_" + UUID.randomUUID() + ".wav";
            Path filePath = resolvedAudioDirPath.resolve(filename);
            Files.write(filePath, response.getBody());

            log.info("Kokoro HTTP TTS saved: {} ({} bytes)", filename, response.getBody().length);
            return filename;

        } catch (Exception e) {
            log.error("Kokoro HTTP fallback error: {}", e.getMessage());
            return null;
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────

    private String toTtsText(String text) {
        if (text == null) return "";
        if (text.length() <= 300) return text;

        // Split on sentence boundaries and take first 2
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length <= 2) return text;

        String shortened = sentences[0] + " " + sentences[1];
        log.debug("TTS text shortened: {} → {} chars", text.length(), shortened.length());
        return shortened;
    }

    public Path resolveAudioFilePath(String filename) {
        return resolvedAudioDirPath.resolve(filename).normalize();
    }

    public String preGenerateQuestionAudio(Long sessionId, Long questionId, String text) {
        if (sessionId == null || questionId == null) {
            return null;
        }

        String filename = synthesize(text);
        if (filename == null) {
            return null;
        }

        generatedBySessionQuestion.put(sessionQuestionKey(sessionId, questionId), filename);
        return buildAudioUrl(filename);
    }

    public String resolveQuestionAudioUrl(Long sessionId, Long questionId, String text) {
        if (sessionId == null || questionId == null) {
            return null;
        }

        String key = sessionQuestionKey(sessionId, questionId);
        String cachedFilename = generatedBySessionQuestion.get(key);
        if (cachedFilename != null && Files.exists(resolvedAudioDirPath.resolve(cachedFilename))) {
            return buildAudioUrl(cachedFilename);
        }

        return preGenerateQuestionAudio(sessionId, questionId, text);
    }

    public void deleteFile(String filename) {
        if (filename == null || !filename.startsWith("tts_")) return;
        try {
            Files.deleteIfExists(resolvedAudioDirPath.resolve(filename));
            generatedBySessionQuestion.entrySet().removeIf(entry -> filename.equals(entry.getValue()));
        } catch (IOException e) {
            log.warn("Could not delete TTS file: {}", filename);
        }
    }

    public void cleanupOldFiles() {
        try (Stream<Path> files = Files.list(resolvedAudioDirPath)) {
            files
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".mp3") || n.endsWith(".wav");
                })
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis()
                            < System.currentTimeMillis() - 3_600_000;
                    } catch (IOException e) { return false; }
                })
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException ignored) {}
                });
        } catch (IOException e) {
            log.error("TTS cleanup failed: {}", e.getMessage());
        }
    }

    private String buildAudioUrl(String filename) {
        String base = (audioBaseUrl == null || audioBaseUrl.isBlank())
                ? "/interview-service/api/v1/audio"
                : audioBaseUrl.trim();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        return base + "/" + filename;
    }

    private String sessionQuestionKey(Long sessionId, Long questionId) {
        return sessionId + ":" + questionId;
    }

    private Path resolveConfiguredPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }

        Path path = Paths.get(configuredPath);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        return path.normalize();
    }

    private String resolveExecutable(String configuredExecutable) {
        if (configuredExecutable == null || configuredExecutable.isBlank()) {
            return "python";
        }

        Path executablePath = Paths.get(configuredExecutable);
        if (executablePath.isAbsolute()) {
            return executablePath.normalize().toString();
        }

        Path normalized = executablePath.toAbsolutePath().normalize();
        if (Files.exists(normalized)) {
            return normalized.toString();
        }

        return configuredExecutable;
    }

    private String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }
}
