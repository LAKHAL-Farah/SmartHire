package tn.esprit.msinterview.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WhisperClient {

    @Value("${smarthire.whisper.binary-path:${whisper.executable.path:./tools/whisper/whisper-cli.exe}}")
    private String whisperBinaryPath;

    @Value("${smarthire.whisper.model-path:${whisper.model.path:./tools/whisper/models/ggml-base.en.bin}}")
    private String modelPath;

    @Value("${whisper.temp.dir:./temp/whisper-audio}")
    private String tempDir;

    @Value("${smarthire.whisper.timeout-seconds:60}")
    private int whisperTimeoutSeconds;

    @Value("${smarthire.whisper.ffmpeg-path:${whisper.ffmpeg.path:ffmpeg}}")
    private String ffmpegBinaryPath;

    private Path resolvedExecutablePath;
    private Path resolvedModelPath;
    private Path resolvedTempDirPath;
    private volatile boolean ffmpegAvailable;
    private volatile String discoveredFfmpegPath;

    @PostConstruct
    public void startupChecks() {
        resolvedExecutablePath = resolveConfiguredPath(whisperBinaryPath, true);
        resolvedModelPath = resolveConfiguredPath(modelPath, true);
        resolvedTempDirPath = resolveConfiguredPath(tempDir, false);

        String binaryPath = executableToUse();
        String modelResolvedPath = modelToUse();
        File bin = binaryPath == null ? new File("") : new File(binaryPath);
        File model = modelResolvedPath == null ? new File("") : new File(modelResolvedPath);

        log.info("[WhisperClient] Binary path: {}", binaryPath);
        log.info("[WhisperClient] Model path: {}", modelResolvedPath);
        log.info("[WhisperClient] Binary exists={} executable={}", bin.exists(), bin.canExecute());
        log.info("[WhisperClient] Model exists={} size={}", model.exists(), model.exists() ? model.length() : 0L);

        log.info("[WhisperSmokeTest] Checking whisper.cpp configuration...");
        log.info("[WhisperSmokeTest] Binary: {} | exists={} | executable={}", binaryPath, bin.exists(), bin.canExecute());
        log.info("[WhisperSmokeTest] Model: {} | exists={} | size={} bytes", modelResolvedPath, model.exists(), model.exists() ? model.length() : 0L);

        if (!bin.exists()) {
            log.error("[WhisperSmokeTest] BINARY NOT FOUND at: {}", binaryPath);
        }
        if (!bin.canExecute()) {
            log.error("[WhisperSmokeTest] BINARY NOT EXECUTABLE: {}", binaryPath);
        }
        if (!model.exists()) {
            log.error("[WhisperSmokeTest] MODEL NOT FOUND at: {}", modelResolvedPath);
        }

        if (bin.exists() && bin.canExecute() && model.exists()) {
            log.info("[WhisperSmokeTest] PASSED - whisper.cpp is configured correctly");
        }

        ensureTempDir();
        ffmpegAvailable = checkFfmpegAvailability();
    }

    private Path resolveConfiguredPath(String rawPath, boolean mustExist) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        Path configured = Paths.get(rawPath.trim());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        List<Path> bases = candidateBaseDirs();
        Path fallback = bases.isEmpty()
                ? configured.toAbsolutePath().normalize()
                : bases.get(0).resolve(configured).normalize();

        for (Path base : bases) {
            Path candidate = base.resolve(configured).normalize();
            if (mustExist) {
                if (Files.exists(candidate)) {
                    return candidate;
                }
            } else {
                return candidate;
            }
        }

        return fallback;
    }

    private List<Path> candidateBaseDirs() {
        Set<Path> bases = new LinkedHashSet<>();

        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        bases.add(cwd);

        Path walk = cwd;
        for (int i = 0; i < 4 && walk.getParent() != null; i++) {
            walk = walk.getParent();
            bases.add(walk);
        }

        Path codeBase = resolveCodeBaseDir();
        if (codeBase != null) {
            bases.add(codeBase);

            Path codeWalk = codeBase;
            for (int i = 0; i < 4 && codeWalk.getParent() != null; i++) {
                codeWalk = codeWalk.getParent();
                bases.add(codeWalk);
            }
        }

        // Common module location when running from workspace root.
        bases.add(cwd.resolve("SmartHire").resolve("MS-Interview").normalize());
        bases.add(cwd.resolve("MS-Interview").normalize());

        return new ArrayList<>(bases);
    }

    private Path resolveCodeBaseDir() {
        try {
            CodeSource codeSource = WhisperClient.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                throw new IllegalStateException("CodeSource location is unavailable");
            }

            Path location = Paths.get(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(location)) {
                Path parent = location.getParent();
                return parent != null ? parent : null;
            }

            return location;
        } catch (URISyntaxException | RuntimeException e) {
            log.warn("Unable to resolve code source location for Whisper paths: {}", e.getMessage());
        }

        try {
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                return null;
            }

            String firstEntry = classPath.split(File.pathSeparator)[0];
            if (firstEntry == null || firstEntry.isBlank()) {
                return null;
            }

            Path classPathEntry = Paths.get(firstEntry).toAbsolutePath().normalize();
            if (Files.isRegularFile(classPathEntry)) {
                return classPathEntry.getParent();
            }

            if (Files.isDirectory(classPathEntry)) {
                return classPathEntry;
            }
        } catch (Exception e) {
            log.warn("Unable to resolve classpath location for Whisper paths: {}", e.getMessage());
        }

        return null;
    }

    private boolean checkFfmpegAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegToUse(), "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("[WhisperSmokeTest] ffmpeg NOT FOUND - audio conversion check timed out");
                ffmpegAvailable = false;
                return false;
            }

            if (process.exitValue() == 0) {
                log.info("[WhisperSmokeTest] ffmpeg available: YES ({})", ffmpegToUse());
                ffmpegAvailable = true;
                return true;
            } else {
                log.error("[WhisperSmokeTest] ffmpeg NOT FOUND - non-zero exit code {}", process.exitValue());
                ffmpegAvailable = false;
                return false;
            }
        } catch (Exception e) {
            log.error("[WhisperSmokeTest] ffmpeg NOT FOUND - audio conversion will fail ({})", e.getMessage());
            ffmpegAvailable = false;
            return false;
        }
    }

    public boolean canTranscribeWebm() {
        return ffmpegAvailable || checkFfmpegAvailability();
    }

    public void assertCanTranscribeWebm() {
        if (!canTranscribeWebm()) {
            throw new IllegalStateException(
                    "Audio transcription requires ffmpeg for webm conversion. Configure smarthire.whisper.ffmpeg-path or add ffmpeg to PATH."
            );
        }
    }

    /**
     * Transcribes an audio file and returns the transcript as a String.
     * The audio file must be WAV format, 16kHz mono for best results.
     * @param audioFilePath absolute path to the saved audio file
     * @return transcript text
     */
    public String transcribe(String audioFilePath) {
        log.info("[WhisperClient] Starting transcription: {}", audioFilePath);

        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            log.error("[WhisperClient] Audio file not found: {}", audioFilePath);
            return "";
        }
        if (audioFile.length() == 0) {
            log.warn("[WhisperClient] Audio file is empty: {}", audioFilePath);
            return "";
        }
        log.info("[WhisperClient] Audio file OK: {} bytes", audioFile.length());

        String processedPath = audioFilePath;
        try {
            processedPath = convertToWhisperFormat(audioFilePath);
            if (processedPath == null || processedPath.isBlank()) {
                log.error("[WhisperClient] Cannot transcribe '{}' because audio conversion failed", audioFilePath);
                return "";
            }

            if (isLikelySilence(processedPath)) {
                log.info("[WhisperClient] Audio appears silent, returning empty transcript");
                return "";
            }

            ProcessBuilder pb = new ProcessBuilder(
                executableToUse(),
                "-m", modelToUse(),
                "-f", processedPath,
                "-l", "en"
            );

            log.info("[WhisperClient] Running command: {}", String.join(" ", pb.command()));

            pb.redirectErrorStream(false);
            Process process = pb.start();

            CompletableFuture<String> stdoutFuture = readAllAsync(process.getInputStream());
            CompletableFuture<String> stderrFuture = readAllAsync(process.getErrorStream());

            boolean finished = process.waitFor(Math.max(1, whisperTimeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("[WhisperClient] whisper.cpp timed out after {}s for file: {}", whisperTimeoutSeconds, processedPath);
                return "";
            }

            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();

            int exitCode = process.exitValue();
            log.info("[WhisperClient] Exit code: {}", exitCode);

            if (!stderr.isBlank()) {
                log.warn("[WhisperClient] stderr: {}", stderr.trim());
            }

            if (exitCode != 0) {
                log.error("[WhisperClient] whisper.cpp exited with code {}", exitCode);
                return "";
            }

                String rawOutput = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
                String cleaned = rawOutput.lines()
                    .map(String::trim)
                    .map(line -> line.replaceFirst("^\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s*-->\\s*\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\]\\s*", ""))
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("whisper_") && !line.startsWith("main:") && !line.startsWith("system_info:"))
                    .filter(line -> !line.startsWith("ffmpeg version") && !line.startsWith("Input #") && !line.startsWith("Output #"))
                    .collect(Collectors.joining(" "))
                    .trim();

            log.info("[WhisperClient] Transcript: \"{}\"", cleaned);
            return cleaned;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[WhisperClient] Transcription interrupted: {}", e.getMessage());
            return "";
        } catch (ExecutionException | IOException e) {
            log.error("[WhisperClient] Transcription error: {}", e.getMessage(), e);
            return "";
        } finally {
            try {
                if (processedPath != null && !processedPath.equals(audioFilePath)) {
                    Files.deleteIfExists(Paths.get(processedPath));
                }
            } catch (Exception ignored) {
                // no-op
            }
        }
    }

    public String transcribeFromWebm(String webmFilePath) {
        assertCanTranscribeWebm();
        return transcribe(webmFilePath);
    }

    private String convertToWhisperFormat(String inputPath) {
        if (inputPath != null && inputPath.toLowerCase().endsWith(".wav")) {
            return inputPath;
        }

        if (!canTranscribeWebm()) {
            log.error("[WhisperClient] ffmpeg unavailable - cannot convert '{}' to wav", inputPath);
            return null;
        }

        String outputName = "whisper_input_" + UUID.randomUUID() + ".wav";
        String outputPath;
        try {
            Path tempBase = resolvedTempDirPath != null ? resolvedTempDirPath : Paths.get(tempDir);
            Files.createDirectories(tempBase);
            outputPath = tempBase.resolve(outputName).toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("[WhisperClient] Could not prepare temp conversion path: {}", e.getMessage());
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegToUse(), "-y",
                    "-i", inputPath,
                    "-ar", "16000",
                    "-ac", "1",
                    "-c:a", "pcm_s16le",
                    outputPath
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String ffmpegOutput = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = p.waitFor(30, TimeUnit.SECONDS);

            if (!finished || p.exitValue() != 0 || !Files.exists(Paths.get(outputPath))) {
                log.error("[WhisperClient] ffmpeg conversion failed: {}", ffmpegOutput.trim());
                return null;
            }

            log.info("[WhisperClient] Converted audio to whisper format: {}", outputPath);
            return outputPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[WhisperClient] ffmpeg conversion interrupted: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[WhisperClient] ffmpeg conversion failed: {}", e.getMessage());
            return null;
        }
    }

    private CompletableFuture<String> readAllAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private boolean isLikelySilence(String inputPath) {
        try {
                ProcessBuilder pb = new ProcessBuilder(
                    ffmpegToUse(),
                    "-i", inputPath,
                    "-af", "volumedetect",
                    "-f", "null",
                    "-"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean finished = p.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }

            String normalized = output.toLowerCase();
            if (normalized.contains("max_volume: -inf db") || normalized.contains("mean_volume: -inf db")) {
                return true;
            }

            String marker = "max_volume:";
            int idx = normalized.lastIndexOf(marker);
            if (idx >= 0) {
                String tail = normalized.substring(idx + marker.length()).trim();
                String value = tail.split("\\s+")[0];
                if (value.endsWith("db")) {
                    value = value.substring(0, value.length() - 2);
                }
                try {
                    double db = Double.parseDouble(value);
                    return db <= -45.0;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }

            return false;
        } catch (Exception e) {
            log.warn("[WhisperClient] Silence detection skipped: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates the temp directory if it does not exist.
     * Called once on startup.
     */
    public void ensureTempDir() {
        try {
            Path dir = resolvedTempDirPath != null ? resolvedTempDirPath : Paths.get(tempDir);
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Could not create Whisper temp dir: {}", e.getMessage());
        }
    }

    private String executableToUse() {
        return resolvedExecutablePath != null ? resolvedExecutablePath.toString() : whisperBinaryPath;
    }

    private String modelToUse() {
        return resolvedModelPath != null ? resolvedModelPath.toString() : modelPath;
    }

    private String ffmpegToUse() {
        if (ffmpegBinaryPath == null || ffmpegBinaryPath.isBlank()) {
            return discoverFfmpegExecutable();
        }

        String configured = ffmpegBinaryPath.trim();
        if (!"ffmpeg".equalsIgnoreCase(configured)) {
            return configured;
        }

        return discoverFfmpegExecutable();
    }

    private String discoverFfmpegExecutable() {
        if (discoveredFfmpegPath != null && !discoveredFfmpegPath.isBlank()) {
            return discoveredFfmpegPath;
        }

        List<Path> candidates = new ArrayList<>();

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(Paths.get(localAppData, "Microsoft", "WinGet", "Links", "ffmpeg.exe"));

            Path wingetPackages = Paths.get(localAppData, "Microsoft", "WinGet", "Packages");
            if (Files.isDirectory(wingetPackages)) {
                try (var dirs = Files.list(wingetPackages)) {
                    dirs.filter(Files::isDirectory)
                            .filter(path -> path.getFileName().toString().startsWith("Gyan.FFmpeg"))
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                Path discovered = findFfmpegUnder(path);
                                if (discovered != null) {
                                    candidates.add(discovered);
                                }
                            });
                } catch (Exception ignored) {
                    // Fallback to PATH when package discovery fails.
                }
            }
        }

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            candidates.add(Paths.get(programFiles, "ffmpeg", "bin", "ffmpeg.exe"));
            candidates.add(Paths.get(programFiles, "FFmpeg", "bin", "ffmpeg.exe"));
        }

        String systemDrive = System.getenv("SystemDrive");
        if (systemDrive != null && !systemDrive.isBlank()) {
            candidates.add(Paths.get(systemDrive + "\\Program Files (x86)", "ffmpeg", "bin", "ffmpeg.exe"));
            candidates.add(Paths.get(systemDrive + "\\Program Files (x86)", "FFmpeg", "bin", "ffmpeg.exe"));
        }

        for (Path candidate : candidates) {
            try {
                if (candidate != null && Files.isRegularFile(candidate)) {
                    discoveredFfmpegPath = candidate.toAbsolutePath().normalize().toString();
                    return discoveredFfmpegPath;
                }
            } catch (Exception ignored) {
                // Continue scanning remaining candidate paths.
            }
        }

        return "ffmpeg";
    }

    private Path findFfmpegUnder(Path root) {
        try (var walk = Files.walk(root, 6)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> "ffmpeg.exe".equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}