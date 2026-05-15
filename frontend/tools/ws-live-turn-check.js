const fs = require('fs');
const { Client } = require('@stomp/stompjs');
const SockJS = require('sockjs-client');

const sessionId = Number(process.argv[2] || '405');
const audioPath = process.argv[3];
const wsUrl = process.argv[4] || 'http://localhost:8081/interview-service/ws-interview';

if (!audioPath || !fs.existsSync(audioPath)) {
  console.error('Usage: node tools/ws-live-turn-check.js <sessionId> <audioPath> [wsUrl]');
  process.exit(1);
}

const audioBytes = fs.readFileSync(audioPath);
const events = [];
let sent = false;
let finished = false;

const client = new Client({
  webSocketFactory: () => new SockJS(wsUrl),
  reconnectDelay: 0,
  heartbeatIncoming: 0,
  heartbeatOutgoing: 0,
  debug: () => {}
});

const timer = setTimeout(() => {
  finalize('timeout');
}, 30000);

function simplify(envelope) {
  const type = String(envelope.type || envelope.eventType || '').toUpperCase();
  const payload = envelope.payload && typeof envelope.payload === 'object' ? envelope.payload : {};
  const simple = {
    type,
    message: envelope.message || null,
    audioUrl: payload.audioUrl || null,
    transcript: payload.transcript || null,
    text: payload.text || null,
    nextQuestionText: payload.nextQuestionText || null,
    answerId: payload.answerId || null,
    bytes: payload.bytes || null,
    isClosing: payload.isClosing === true
  };
  return simple;
}

function finalize(reason) {
  if (finished) {
    return;
  }
  finished = true;
  clearTimeout(timer);

  Promise.resolve(client.deactivate())
    .catch(() => null)
    .finally(() => {
      const hasTranscript = events.some(e => e.type === 'LIVE_TRANSCRIPT');
      const hasAiSpeech = events.some(e => e.type === 'LIVE_AI_SPEECH');
      const hasError = events.some(e => e.type === 'ERROR');

      console.log(JSON.stringify({
        reason,
        sessionId,
        sentBytes: audioBytes.length,
        hasTranscript,
        hasAiSpeech,
        hasError,
        events
      }, null, 2));
      process.exit(0);
    });
}

function safePublish(frame, label) {
  if (!client.connected) {
    events.push({ type: 'PUBLISH_SKIPPED', message: `${label}: disconnected` });
    return false;
  }

  try {
    client.publish(frame);
    return true;
  } catch (err) {
    events.push({ type: 'PUBLISH_ERROR', message: `${label}: ${String(err)}` });
    return false;
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

client.onConnect = () => {
  client.subscribe(`/topic/session/${sessionId}`, frame => {
    try {
      const envelope = JSON.parse(frame.body);
      const entry = simplify(envelope);
      events.push(entry);

      if (entry.type === 'LIVE_AI_SPEECH' || entry.type === 'ERROR') {
        setTimeout(() => finalize('received_terminal_event'), 500);
      }
    } catch (err) {
      events.push({ type: 'PARSE_ERROR', message: String(err) });
    }
  });

  safePublish({
    destination: `/app/session/${sessionId}/bootstrap`,
    body: ''
  }, 'bootstrap');

  setTimeout(async () => {
    if (sent) {
      return;
    }
    sent = true;

    const chunkSize = 8192;
    for (let i = 0; i < audioBytes.length; i += chunkSize) {
      const part = audioBytes.subarray(i, i + chunkSize);
      const encodedChunk = Buffer.from(part).toString('base64');
      const sentChunk = safePublish({
        destination: `/app/session/${sessionId}/audio-chunk`,
        headers: { 'content-type': 'text/plain' },
        body: `b64:${encodedChunk}`
      }, `audio-chunk-${Math.floor(i / chunkSize)}`);

      if (!sentChunk) {
        finalize('publish_chunk_failed');
        return;
      }

      await sleep(25);
    }

    setTimeout(() => {
      const sentEndTurn = safePublish({
        destination: `/app/session/${sessionId}/end-turn`,
        body: ''
      }, 'end-turn');

      if (!sentEndTurn) {
        finalize('publish_end_turn_failed');
      }
    }, 250);
  }, 800);
};

client.onStompError = frame => {
  events.push({ type: 'STOMP_ERROR', message: frame.headers && frame.headers.message ? frame.headers.message : 'stomp error' });
  finalize('stomp_error');
};

client.onWebSocketError = () => {
  events.push({ type: 'WS_ERROR', message: 'websocket error' });
  finalize('ws_error');
};

client.activate();
