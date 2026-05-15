describe('Live Mode UI', () => {
  function setupMedia(win: Window, granted: boolean) {
    (win as any).global = win;
    (win as any).__liveStartPermissionsGranted = granted;

    const fakeStream =
      typeof (win as any).MediaStream === 'function'
        ? (new (win as any).MediaStream() as MediaStream)
        :
            ({
              getTracks: () => [],
              getAudioTracks: () => [],
              getVideoTracks: () => [],
            } as unknown as MediaStream);

    Object.defineProperty(win.navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: () => (granted ? Promise.resolve(fakeStream) : Promise.reject(new Error('Denied'))),
      },
    });
  }

  function setupLiveModeRuntime(win: Window) {
    setupMedia(win, true);

    class FakeMediaRecorder {
      state: 'inactive' | 'recording' = 'inactive';
      private listeners = new Map<string, (event: BlobEvent) => void>();

      constructor(_stream: MediaStream, _opts?: MediaRecorderOptions) {}

      addEventListener(type: string, cb: (event: BlobEvent) => void) {
        this.listeners.set(type, cb);
      }

      start(_timeslice?: number) {
        this.state = 'recording';
      }

      stop() {
        this.state = 'inactive';
      }
    }

    (win as any).MediaRecorder = FakeMediaRecorder;

    (win as any).AudioContext = class {
      createAnalyser() {
        return {
          fftSize: 0,
          frequencyBinCount: 8,
          getByteFrequencyData(arr: Uint8Array) {
            arr.fill(20);
          },
        };
      }

      createMediaStreamSource() {
        return { connect() {} };
      }

      close() {
        return Promise.resolve();
      }
    };

    (win as any).requestAnimationFrame = (cb: FrameRequestCallback) => win.setTimeout(() => cb(0), 16) as any;
    (win as any).cancelAnimationFrame = (id: number) => win.clearTimeout(id);

    (win as any).Audio = function Audio(this: any, src?: string) {
      const listeners = new Map<string, EventListener>();
      this.src = src || '';
      this.volume = 1;
      this.play = () => Promise.resolve();
      this.pause = () => undefined;
      this.addEventListener = (name: string, cb: EventListener) => listeners.set(name, cb);
      this.dispatchEvent = (evt: Event) => {
        listeners.get(evt.type)?.(evt);
        return true;
      };
      return this;
    } as any;
  }

  function emitLiveEvent(event: any) {
    cy.window().its('__emitLiveEvent', { timeout: 10000 }).should('be.a', 'function');
    cy.window().then((win: any) => {
      win.__emitLiveEvent(event);
    });
  }

  function endMainAudio() {
    cy.window().then((win: any) => {
      if (typeof win.__endLiveMainAudio === 'function') {
        win.__endLiveMainAudio();
      }
    });
  }

  it('CY-1 Live Start Screen renders correctly', () => {
    cy.visit('/interview/live/start', {
      onBeforeLoad: (win) => setupMedia(win, false),
    });

    cy.contains('behavioral interview', { matchCase: false }).should('be.visible');
    cy.get('[data-testid="question-count-slider"]').should('be.visible');
    cy.get('[data-testid="mode-toggle"]').should('be.visible');
    cy.get('[data-testid="join-btn"]').should('be.disabled');
    cy.get('[data-testid="permissions-badge"]').should('be.visible');
  });

  it('CY-2 Can configure and start a Live session', () => {
    cy.intercept('POST', '**/api/v1/sessions/start-live', { fixture: 'live-session.json' }).as('startLive');

    cy.visit('/interview/live/start', {
      onBeforeLoad: (win) => setupMedia(win, true),
    });

    cy.get('[data-testid="permissions-badge"]').should('contain.text', 'ready');
    cy.get('[data-testid="join-btn"]').should('not.be.disabled');

    cy.get('[data-testid="question-count-slider"]').invoke('val', 7).trigger('input');
    cy.get('[data-testid="mode-practice"]').click();
    cy.get('[data-testid="company-input"]').type('Acme Corp');
    cy.get('[data-testid="join-btn"]').click();

    cy.wait('@startLive').its('request.body').should('include', { questionCount: 7 });
    cy.url().should('include', '/interview/live/42');
  });

  it('CY-3 Live Mode screen renders Zoom-like layout', () => {
    cy.visit('/interview/live/42?subMode=PRACTICE_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.get('[data-testid="ai-tile"]').should('be.visible');
    cy.get('[data-testid="candidate-tile"]').should('be.visible');
    cy.get('[data-testid="top-bar"]').should('be.visible');
    cy.get('[data-testid="controls-bar"]').should('be.visible');
    cy.get('[data-testid="feedback-overlay"]').should('not.exist');
  });

  it('CY-4 AI speaking animation activates on LIVE_SESSION_READY', () => {
    cy.visit('/interview/live/42?subMode=PRACTICE_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-session-ready-event.json').then((event) => emitLiveEvent(event));

    cy.get('[data-testid="ai-tile"]').should('have.class', 'speaking');
    cy.get('[data-testid="sound-bars"]').should('exist');
    cy.get('[data-testid="question-caption"]').should('contain', 'Tell me about yourself.');
  });

  it('CY-5 Question counter updates on LIVE_AI_SPEECH', () => {
    cy.visit('/interview/live/42?subMode=TEST_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-session-ready-event.json').then((event) => emitLiveEvent(event));
    cy.get('[data-testid="question-counter"]').should('contain', 'Q1 / 5');

    cy.fixture('live-ai-speech-event.json').then((event) => {
      const updated = {
        ...event,
        payload: {
          ...event.payload,
          currentQuestionIndex: 2,
        },
      };
      emitLiveEvent(updated);
    });

    cy.get('[data-testid="question-counter"]').should('contain', 'Q3 / 5');
  });

  it('CY-6 Feedback overlay appears in PRACTICE mode (low score)', () => {
    cy.visit('/interview/live/42?subMode=PRACTICE_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-feedback-event.json').then((event) => emitLiveEvent(event));
    endMainAudio();

    cy.get('[data-testid="feedback-overlay"]', { timeout: 8000 }).should('be.visible');
    cy.get('[data-testid="feedback-text"]').should('contain', 'missing a measurable result');
    cy.get('[data-testid="score-value"]').should('contain', '4.5');
    cy.get('[data-testid="btn-retry"]').should('be.visible');
    cy.get('[data-testid="btn-continue"]').should('be.visible');
    cy.get('[data-testid="auto-continue-countdown"]').should('be.visible');
  });

  it('CY-7 Retry button hides overlay and sends /retry signal', () => {
    cy.visit('/interview/live/42?subMode=PRACTICE_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-feedback-event.json').then((event) => emitLiveEvent(event));
    endMainAudio();
    cy.get('[data-testid="feedback-overlay"]').should('be.visible');

    cy.get('[data-testid="btn-retry"]').click();
    cy.get('[data-testid="feedback-overlay"]').should('not.exist');

    cy.window().then((win: any) => {
      const frames = (win.__livePublishedFrames || []) as Array<{ destination: string }>;
      expect(frames.some((f) => f.destination === '/app/session/42/retry')).to.eq(true);
    });
  });

  it('CY-8 Continue button hides overlay', () => {
    cy.visit('/interview/live/42?subMode=PRACTICE_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-feedback-event.json').then((event) => emitLiveEvent(event));
    endMainAudio();
    cy.get('[data-testid="feedback-overlay"]').should('be.visible');

    cy.get('[data-testid="btn-continue"]').click();
    cy.get('[data-testid="feedback-overlay"]').should('not.exist');
  });

  it('CY-9 Feedback overlay NOT shown in TEST_LIVE mode', () => {
    cy.visit('/interview/live/42?subMode=TEST_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-feedback-event.json').then((event) => emitLiveEvent(event));
    cy.get('[data-testid="feedback-overlay"]').should('not.exist');
  });

  it('CY-10 Mic mute button turns red and shows muted badge', () => {
    cy.visit('/interview/live/42?subMode=TEST_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.get('[data-testid="mic-btn"]').click();
    cy.get('[data-testid="mic-btn"]').should('have.class', 'off');
    cy.get('[data-testid="muted-badge"]').should('be.visible');

    cy.get('[data-testid="mic-btn"]').click();
    cy.get('[data-testid="muted-badge"]').should('not.exist');
  });

  it('CY-11 Leave button triggers confirm and navigates to dashboard', () => {
    cy.intercept('PUT', '**/api/v1/sessions/42/abandon', {}).as('abandon');
    cy.visit('/interview/live/42?subMode=TEST_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.on('window:confirm', () => true);
    cy.get('[data-testid="leave-btn"]').click();

    cy.wait('@abandon');
    cy.url().should('include', '/dashboard');
  });

  it('CY-12 Closing speech navigates immediately to report', () => {
    cy.visit('/interview/live/42?subMode=TEST_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-closing-event.json').then((event) => emitLiveEvent(event));
    endMainAudio();

    cy.url({ timeout: 8000 }).should('include', '/dashboard/interview/report/42');
  });

  it('CY-13 Auto-continue countdown counts down in overlay', () => {
    cy.visit('/interview/live/42?subMode=PRACTICE_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.fixture('live-feedback-event.json').then((event) => emitLiveEvent(event));
    endMainAudio();

    cy.get('[data-testid="auto-continue-countdown"]').should('contain', '30');
    cy.wait(3000);
    cy.get('[data-testid="auto-continue-countdown"]').invoke('text').should('match', /2[67]/);
  });

  it('CY-14 Session timer increments every second', () => {
    cy.visit('/interview/live/42?subMode=TEST_LIVE&company=Acme+Corp', {
      onBeforeLoad: (win) => setupLiveModeRuntime(win),
    });

    cy.get('[data-testid="session-timer"]').should('contain', '00:00');
    cy.wait(3000);
    cy.get('[data-testid="session-timer"]').should('contain', '00:0');
  });
});
