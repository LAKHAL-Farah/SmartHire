import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { MLScenarioAnswer } from '../models/ml-scenario-answer.model';

@Injectable({ providedIn: 'root' })
export class MlAnswerService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${this.resolveBaseUrl()}/ml-answers`;

  triggerExtraction(answerId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/extract/${answerId}`, {});
  }

  getMLAnswer(answerId: number): Observable<MLScenarioAnswer> {
    return this.http.get<unknown>(`${this.baseUrl}/answer/${answerId}`).pipe(
      map((raw) => this.normalize(raw))
    );
  }

  getSessionMLAnswers(sessionId: number): Observable<MLScenarioAnswer[]> {
    return this.http.get<unknown[]>(`${this.baseUrl}/session/${sessionId}`).pipe(
      map((rows) => rows.map((row) => this.normalize(row)))
    );
  }

  private normalize(raw: unknown): MLScenarioAnswer {
    const source = (raw ?? {}) as Record<string, unknown>;
    return {
      id: this.toNumber(source['id']) ?? 0,
      answerId: this.toNumber(source['answerId']) ?? 0,
      modelChosen: this.toStringOrNull(source['modelChosen']),
      features: this.parseArray(source['features'] ?? source['featuresDescribed']),
      metrics: this.parseArray(source['metrics'] ?? source['metricsDescribed']),
      deployment: this.toStringOrNull(source['deployment'] ?? source['deploymentStrategy']),
      dataPreprocessing: this.toStringOrNull(source['dataPreprocessing']),
      evaluationStrategy: this.toStringOrNull(source['evaluationStrategy']),
      mlScore: this.toNumber(source['mlScore'] ?? source['aiEvaluationScore']),
      mlFeedback: this.toStringOrNull(source['mlFeedback']),
      followUpGenerated: this.toStringOrNull(source['followUpGenerated']),
    };
  }

  private parseArray(value: unknown): string[] | null {
    if (Array.isArray(value)) {
      const normalized = value.map((entry) => String(entry).trim()).filter((entry) => entry.length > 0);
      return normalized.length ? normalized : null;
    }

    if (typeof value !== 'string') {
      return null;
    }

    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }

    try {
      const parsed = JSON.parse(trimmed) as unknown;
      if (Array.isArray(parsed)) {
        const normalized = parsed.map((entry) => String(entry).trim()).filter((entry) => entry.length > 0);
        return normalized.length ? normalized : null;
      }
    } catch {
      // Fallback to comma splitting.
    }

    const split = trimmed
      .split(',')
      .map((entry) => entry.trim())
      .filter((entry) => entry.length > 0);

    return split.length ? split : null;
  }

  private toStringOrNull(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null;
    }

    const trimmed = value.trim();
    return trimmed.length ? trimmed : null;
  }

  private toNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }

    if (typeof value === 'string') {
      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : null;
    }

    return null;
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }

    return '/interview-service/api/v1';
  }
}
