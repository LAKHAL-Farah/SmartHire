package tn.esprit.msinterview.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class LiveAudioBuffer {

    private final Map<Long, List<byte[]>> buffers = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSeen = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();
    private final Set<Long> processing = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService silenceScheduler;
    private final ObjectProvider<LiveAnswerProcessor> answerProcessorProvider;

    @Value("${smarthire.live.silence.threshold.ms:4500}")
    private long silenceThresholdMs;

    public void initSession(Long sessionId) {
        buffers.put(sessionId, Collections.synchronizedList(new ArrayList<>()));
        lastSeen.put(sessionId, System.currentTimeMillis());
        log.debug("[LiveBuffer] Initialized session {}", sessionId);
    }

    public void receiveChunk(Long sessionId, byte[] chunk) {
        if (sessionId == null || chunk == null || chunk.length == 0) {
            return;
        }

        if (!buffers.containsKey(sessionId)) {
            log.warn("[LiveBuffer] Unknown session {} - auto-initializing", sessionId);
            initSession(sessionId);
        }

        buffers.get(sessionId).add(chunk);
        log.info("[LiveBuffer] Received chunk session={} bytes={}", sessionId, chunk.length);
        lastSeen.put(sessionId, System.currentTimeMillis());
        resetTimer(sessionId);
    }

    public void forceSeal(Long sessionId) {
        log.info("[LiveBuffer] forceSeal called for session={}", sessionId);
        ScheduledFuture<?> timer = timers.remove(sessionId);
        if (timer != null) {
            timer.cancel(false);
        }
        sealAndProcess(sessionId);
    }

    private void resetTimer(Long sessionId) {
        ScheduledFuture<?> old = timers.get(sessionId);
        if (old != null) {
            old.cancel(false);
        }

        timers.put(sessionId, silenceScheduler.schedule(
                () -> sealAndProcess(sessionId),
                silenceThresholdMs,
                TimeUnit.MILLISECONDS
        ));
    }

    private void sealAndProcess(Long sessionId) {
        if (!processing.add(sessionId)) {
            log.warn("[LiveBuffer] Already processing session {} - skip duplicate seal", sessionId);
            return;
        }

        List<byte[]> chunks = buffers.remove(sessionId);
        timers.remove(sessionId);

        if (chunks == null || chunks.isEmpty()) {
            log.debug("[LiveBuffer] Silence fired for session {} but buffer empty", sessionId);
            processing.remove(sessionId);
            initSession(sessionId);
            return;
        }

        int total = chunks.stream().mapToInt(c -> c.length).sum();
        byte[] merged = new byte[total];
        int offset = 0;

        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }

        log.info("[LiveBuffer] Sealed session {} - {} chunks, {} bytes -> processing", sessionId, chunks.size(), total);

        try {
            answerProcessorProvider.getObject().processAudio(sessionId, merged);
        } finally {
            processing.remove(sessionId);
            initSession(sessionId);
        }
    }

    public void clearSession(Long sessionId) {
        buffers.remove(sessionId);
        lastSeen.remove(sessionId);
        processing.remove(sessionId);
        ScheduledFuture<?> timer = timers.remove(sessionId);
        if (timer != null) {
            timer.cancel(true);
        }
        log.info("[LiveBuffer] Session {} cleared", sessionId);
    }

    public boolean hasActiveBuffer(Long sessionId) {
        return buffers.containsKey(sessionId);
    }
}
