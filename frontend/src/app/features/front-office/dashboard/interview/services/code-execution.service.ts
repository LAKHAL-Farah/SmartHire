import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TestCaseResult {
  index: number;
  input: string;
  expectedOutput: string;
  actualOutput: string;
  stderr?: string;
  statusDescription?: string;
  passed: boolean;
  isHidden: boolean;
}

export interface CodeExecutionResponse {
  testCaseResults: TestCaseResult[];
  passedCount: number;
  totalCount: number;
  allPassed: boolean;
  answerId: number;
  stderr?: string;
  statusDescription?: string;
}

@Injectable({ providedIn: 'root' })
export class CodeExecutionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${this.resolveBaseUrl()}/code`;

  runCode(
    answerId: number,
    questionId: number,
    sourceCode: string,
    language: string
  ): Observable<CodeExecutionResponse> {
    return this.http.post<CodeExecutionResponse>(`${this.base}/execute`, {
      answerId,
      questionId,
      sourceCode,
      language,
    });
  }

  submitFinal(
    sessionId: number,
    questionId: number,
    sourceCode: string,
    language: string,
    explanation: string
  ): Observable<any> {
    return this.http.post(`${this.base}/submit`, {
      sessionId,
      questionId,
      sourceCode,
      language,
      explanation,
    });
  }

  getLatestResult(answerId: number): Observable<any> {
    return this.http.get(`${this.base}/results/${answerId}/latest`);
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }

    return '/interview-service/api/v1';
  }
}
