package tn.esprit.msinterview.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.esprit.msinterview.config.LiveModeScripts;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
@RequiredArgsConstructor
public class FillerAudioCache {

    private final TTSClient ttsClient;
    private final LiveModeScripts scripts;

    private final List<String> fillerUrls = new ArrayList<>();

    @Value("${smarthire.audio.base-url:/interview-service/audio}")
    private String audioBaseUrl;

    @PostConstruct
    public void preGenerate() {
        List<String> phrases = scripts.getFillers();
        if (phrases == null || phrases.isEmpty()) {
            log.warn("[FillerCache] No filler phrases found in config - check live-mode-scripts.yml");
            return;
        }

        log.info("[FillerCache] Pre-generating {} filler phrases...", phrases.size());
        fillerUrls.clear();
        int success = 0;

        for (String phrase : phrases) {
            try {
                String generated = ttsClient.synthesize(phrase);
                if (generated == null || generated.isBlank()) {
                    continue;
                }

                String filename = Paths.get(generated).getFileName().toString();
                String url = buildAudioUrl(filename);
                fillerUrls.add(url);
                log.debug("[FillerCache] Cached: \"{}\" -> {}", phrase, url);
                success++;
            } catch (Exception e) {
                log.warn("[FillerCache] Failed to generate filler \"{}\": {}", phrase, e.getMessage());
            }
        }

        log.info("[FillerCache] Ready: {}/{} phrases cached", success, phrases.size());
        if (success == 0) {
            log.error("[FillerCache] Zero fillers cached - filler playback will be skipped");
        }
    }

    public String getRandomFillerUrl() {
        if (fillerUrls.isEmpty()) {
            return null;
        }
        return fillerUrls.get(ThreadLocalRandom.current().nextInt(fillerUrls.size()));
    }

    public int size() {
        return fillerUrls.size();
    }

    private String buildAudioUrl(String filename) {
        String normalizedBase = audioBaseUrl.endsWith("/")
                ? audioBaseUrl.substring(0, audioBaseUrl.length() - 1)
                : audioBaseUrl;
        return normalizedBase + "/" + filename;
    }
}
