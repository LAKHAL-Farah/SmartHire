import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const apiBase = 'http://localhost:8081/interview-service/api/v1';
const wsUrl = 'http://localhost:8081/interview-service/ws';

async function http(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} :: ${text}`);
  }

  if (!text) {
    return null;
  }

  return JSON.parse(text);
}

async function main() {
  const started = await http('/sessions/start', {
    method: 'POST',
    body: JSON.stringify({
      userId: 1,
      careerPathId: 1,
      role: 'SE',
      mode: 'PRACTICE',
      type: 'TECHNICAL',
      questionCount: 5,
    }),
  });

  const sessionId = started?.id;
  if (!sessionId) {
    throw new Error('No session id returned from start endpoint');
  }

  const currentQuestion = await http(`/sessions/${sessionId}/questions/current`);
  const questionId = currentQuestion?.id;
  if (!questionId) {
    throw new Error('No current question id returned');
  }

  const events = [];
  let connectedResolve;
  let connectedReject;
  const connectedPromise = new Promise((resolve, reject) => {
    connectedResolve = resolve;
    connectedReject = reject;
  });

  const client = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 0,
    onConnect: () => {
      client.subscribe(`/topic/session/${sessionId}`, (message) => {
        try {
          const payload = JSON.parse(message.body);
          events.push(payload);
        } catch {
          // Ignore malformed payloads
        }
      });
      connectedResolve(true);
    },
    onStompError: (frame) => {
      connectedReject(new Error(`STOMP error: ${frame.headers?.message || 'unknown'}`));
    },
    onWebSocketError: (event) => {
      connectedReject(new Error(`WebSocket error: ${String(event)}`));
    },
  });

  client.activate();
  await Promise.race([
    connectedPromise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('WebSocket connect timeout')), 10000)),
  ]);

  function waitForEvent(type, timeoutMs) {
    return new Promise((resolve, reject) => {
      const start = Date.now();
      const timer = setInterval(() => {
        const match = events.find((event) => event?.eventType === type);
        if (match) {
          clearInterval(timer);
          resolve(match);
          return;
        }

        if (Date.now() - start > timeoutMs) {
          clearInterval(timer);
          reject(new Error(`Timeout waiting for ${type}`));
        }
      }, 250);
    });
  }

  // Force a deterministic server push to verify WS subscription path first.
  await http(`/sessions/${sessionId}/questions/next`, { method: 'GET' });
  const nextQuestionEvent = await waitForEvent('NEXT_QUESTION', 15000);

  const currentAfterAdvance = await http(`/sessions/${sessionId}/questions/current`);
  const activeQuestionId = currentAfterAdvance?.id || questionId;

  const submitted = await http('/answers/submit', {
    method: 'POST',
    body: JSON.stringify({
      sessionId,
      questionId: activeQuestionId,
      answerText: 'I would use Spring Boot services, Redis cache, and resilient retries with metrics.',
    }),
  });

  const answerId = submitted?.id;
  if (!answerId) {
    throw new Error('No answer id returned from submit endpoint');
  }

  let evaluationEvent = null;
  try {
    evaluationEvent = await waitForEvent('EVALUATION_READY', 120000);
  } catch {
    evaluationEvent = await http(`/evaluations/answer/${answerId}`);
  }

  client.deactivate();

  const result = {
    sessionId,
    questionId: activeQuestionId,
    answerId,
    receivedEventTypes: [...new Set(events.map((event) => event?.eventType).filter(Boolean))],
    evaluationEvent,
    nextQuestionEvent,
    eventCount: events.length,
  };

  console.log(JSON.stringify(result, null, 2));
}

main().catch((error) => {
  console.error(error?.stack || String(error));
  process.exit(1);
});
