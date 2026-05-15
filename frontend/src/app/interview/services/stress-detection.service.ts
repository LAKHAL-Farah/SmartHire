import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Subscription, firstValueFrom, interval, of } from 'rxjs';
import { catchError, filter, switchMap } from 'rxjs/operators';

export interface StressResult {
  face_detected: boolean;
  stress_score: number;
  level: 'low' | 'medium' | 'high';
  ear?: number;
  brow_furrow?: number;
}

export interface StressReading {
  result: StressResult;
  timestamp: number;
  questionId: number | null;
}

export interface StressQuestionSummary {
  questionId: number;
  avgScore: number;
  level: 'low' | 'medium' | 'high';
  readingCount: number;
  timeline: number[];
}

@Injectable({ providedIn: 'root' })
export class StressDetectionService implements OnDestroy {
  private videoEl: HTMLVideoElement | null = null;
  private canvas: HTMLCanvasElement = document.createElement('canvas');
  private pollSub?: Subscription;

  stressResult$ = new Subject<StressResult>();
  active = false;

  private currentQuestionId: number | null = null;
  private currentSessionId: number | null = null;
  private readings: StressReading[] = [];

  private recentScores: number[] = [];
  private readonly rollingWindow = 5;
  private readonly pythonAnalyzeUrl = 'http://127.0.0.1:8000/analyze';
  private readonly backendBaseUrl = this.resolveBackendBaseUrl();

  constructor(private readonly http: HttpClient) {}

  async start(videoElement: HTMLVideoElement, sessionId: number): Promise<void> {
    this.videoEl = videoElement;
    this.currentSessionId = sessionId;
    this.currentQuestionId = null;
    this.readings = [];
    this.recentScores = [];
    this.active = true;

    this.startPolling();
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();

    this.pollSub = interval(800)
      .pipe(
        filter(() => this.active && !!this.videoEl),
        switchMap(() => {
          const frameBlob = this.captureFrame();
          if (!frameBlob) {
            return of(null);
          }

          const formData = new FormData();
          formData.append('frame', frameBlob, 'frame.jpg');

          return this.http.post<Partial<StressResult>>(this.pythonAnalyzeUrl, formData).pipe(
            catchError(() => of(null))
          );
        }),
        filter((result): result is Partial<StressResult> => result !== null)
      )
      .subscribe((rawResult) => {
        const baseScore = this.toSafeScore(rawResult.stress_score);
        const hasFace = !!rawResult.face_detected;

        if (!hasFace) {
          this.recentScores = [];
          this.stressResult$.next({
            face_detected: false,
            stress_score: 0,
            level: 'low',
            ear: this.toSafeScore(rawResult.ear),
            brow_furrow: this.toSafeScore(rawResult.brow_furrow),
          });
          return;
        }

        this.recentScores.push(baseScore);
        if (this.recentScores.length > this.rollingWindow) {
          this.recentScores.shift();
        }

        const smoothed = this.recentScores.reduce((sum, value) => sum + value, 0) / this.recentScores.length;
        const roundedSmoothed = Math.round(smoothed * 1000) / 1000;

        const result: StressResult = {
          face_detected: true,
          stress_score: roundedSmoothed,
          level: this.levelFromScore(roundedSmoothed),
          ear: this.toSafeScore(rawResult.ear),
          brow_furrow: this.toSafeScore(rawResult.brow_furrow),
        };

        this.readings.push({
          result,
          timestamp: Date.now(),
          questionId: this.currentQuestionId,
        });

        this.stressResult$.next(result);

        if (this.currentSessionId && this.currentQuestionId !== null) {
          this.http
            .post(`${this.backendBaseUrl}/stress/${this.currentSessionId}`, {
              userId: String(this.resolveUserId()),
              sessionId: this.currentSessionId,
              questionId: this.currentQuestionId,
              stressScore: result.stress_score,
              level: result.level,
              ear: result.ear ?? 0,
              browFurrow: result.brow_furrow ?? 0,
              timestamp: Date.now(),
            })
            .pipe(catchError(() => of(null)))
            .subscribe();
        }
      });
  }

  private captureFrame(): Blob | null {
    try {
      if (!this.videoEl || this.videoEl.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
        return null;
      }

      this.canvas.width = 320;
      this.canvas.height = 240;
      const context = this.canvas.getContext('2d');
      if (!context) {
        return null;
      }

      context.drawImage(this.videoEl, 0, 0, 320, 240);

      const dataUrl = this.canvas.toDataURL('image/jpeg', 0.75);
      const content = dataUrl.split(',')[1];
      if (!content) {
        return null;
      }

      const byteString = atob(content);
      const bytes = new Uint8Array(byteString.length);
      for (let i = 0; i < byteString.length; i += 1) {
        bytes[i] = byteString.charCodeAt(i);
      }

      return new Blob([bytes.buffer], { type: 'image/jpeg' });
    } catch {
      return null;
    }
  }

  setCurrentQuestion(questionId: number | null): void {
    this.currentQuestionId = questionId;
    this.recentScores = [];
  }

  async finalizeCurrentQuestion(): Promise<StressQuestionSummary | null> {
    if (!this.currentSessionId || !this.currentQuestionId) {
      return null;
    }

    try {
      return await firstValueFrom(
        this.http
          .post<StressQuestionSummary>(
            `${this.backendBaseUrl}/stress/${this.currentSessionId}/question/${this.currentQuestionId}/finalize`,
            {}
          )
          .pipe(catchError(() => of(null)))
      );
    } catch {
      return null;
    }
  }

  getLatestScore(): number {
    if (!this.recentScores.length) {
      return 0;
    }
    return this.recentScores.reduce((sum, value) => sum + value, 0) / this.recentScores.length;
  }

  stop(): void {
    this.active = false;
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
    this.videoEl = null;
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private resolveBackendBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }

    return '/api/v1';
  }

  private resolveUserId(): number {
    const localStorageUser = globalThis.localStorage?.getItem('userId') ?? '';
    const parsed = Number(localStorageUser);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
    return 99;
  }

  private toSafeScore(value: unknown): number {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return Math.max(0, Math.min(1, value));
    }
    return 0;
  }

  private levelFromScore(score: number): 'low' | 'medium' | 'high' {
    if (score > 0.6) {
      return 'high';
    }
    if (score > 0.35) {
      return 'medium';
    }
    return 'low';
  }
}
