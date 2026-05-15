package tn.esprit.msinterview.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TTSClientBehaviorTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void teardown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void synthesizeUsesElevenLabsAsPrimaryWhenAvailable() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/text-to-speech/voice-test", new Mp3OkHandler());
        server.start();

        Path audioDir = tempDir.resolve("audio-primary");
        Files.createDirectories(audioDir);

        Path dummyScript = tempDir.resolve("scripts").resolve("tts.py");
        Files.createDirectories(dummyScript.getParent());
        Files.writeString(dummyScript, "# not used in this test", StandardCharsets.UTF_8);

        TTSClient client = createClient(
                elevenBaseUrl(),
                audioDir,
                isWindows() ? "cmd.exe" : "sh",
                dummyScript.toString()
        );

        String filename = client.synthesize("Welcome to your SmartHire AI interview.");
        assertNotNull(filename);
        assertTrue(filename.endsWith(".mp3"));

        Path generated = client.resolveAudioFilePath(filename);
        assertTrue(Files.exists(generated));
        assertTrue(Files.size(generated) > 0);
    }

    @Test
    void synthesizeFallsBackToKokoroWhenElevenLabsFails() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/text-to-speech/voice-test", new Error429Handler());
        server.start();

        Path audioDir = tempDir.resolve("audio-fallback");
        Files.createDirectories(audioDir);

        Path scriptsDir = tempDir.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path kokoroScriptPath = scriptsDir.resolve("tts.py");
        Files.writeString(kokoroScriptPath, "# fallback arg placeholder", StandardCharsets.UTF_8);

        Path executable;
        if (isWindows()) {
            executable = scriptsDir.resolve("kokoro-fallback.cmd");
            String cmd = String.join(System.lineSeparator(),
                    "@echo off",
                    "set OUTPUT=%3",
                    "echo RIFF>\"%OUTPUT%\"",
                    "echo OK",
                    "exit /b 0");
            Files.writeString(executable, cmd, StandardCharsets.UTF_8);
        } else {
            executable = scriptsDir.resolve("kokoro-fallback.sh");
            String sh = String.join("\n",
                    "#!/usr/bin/env sh",
                    "out=\"$3\"",
                    "printf 'RIFF' > \"$out\"",
                    "echo OK",
                    "exit 0");
            Files.writeString(executable, sh, StandardCharsets.UTF_8);
            executable.toFile().setExecutable(true);
        }

        TTSClient client = createClient(
                elevenBaseUrl(),
                audioDir,
                executable.toString(),
                kokoroScriptPath.toString()
        );

        String filename = client.synthesize("Please explain your architecture decisions.");
        assertNotNull(filename);
        assertTrue(filename.endsWith(".wav"));

        Path generated = client.resolveAudioFilePath(filename);
        assertTrue(Files.exists(generated));
        assertTrue(Files.size(generated) > 0);
    }

    private String elevenBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private TTSClient createClient(String baseUrl, Path audioDir, String pythonExecutable, String kokoroScriptPath) {
        TTSClient client = new TTSClient();

        ReflectionTestUtils.setField(client, "elevenLabsApiKey", "test-key");
        ReflectionTestUtils.setField(client, "elevenLabsBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "voiceId", "voice-test");
        ReflectionTestUtils.setField(client, "modelId", "eleven_turbo_v2_5");
        ReflectionTestUtils.setField(client, "connectTimeout", 1000);
        ReflectionTestUtils.setField(client, "readTimeout", 1000);

        ReflectionTestUtils.setField(client, "pythonExecutable", pythonExecutable);
        ReflectionTestUtils.setField(client, "kokoroScriptPath", kokoroScriptPath);
        ReflectionTestUtils.setField(client, "audioDir", audioDir.toString());
        ReflectionTestUtils.setField(client, "audioBaseUrl", "/api/v1/audio");

        client.init();
        return client;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static class Mp3OkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = "FAKE_MP3_DATA".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private static class Error429Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = "rate-limited".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
