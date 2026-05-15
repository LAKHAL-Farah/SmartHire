import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SessionAnswerDto } from '../interview.models';

@Injectable({ providedIn: 'root' })
export class AnswerService {
  private readonly http = inject(HttpClient);
  private readonly base = `${this.resolveBaseUrl()}/answers`;

  submitTextAnswer(sessionId: number, questionId: number, answerText: string, codeAnswer?: string): Observable<SessionAnswerDto> {
    return this.http.post<SessionAnswerDto>(`${this.base}/submit`, {
      sessionId,
      questionId,
      answerText,
      codeAnswer: codeAnswer ?? null,
      videoUrl: null,
      audioUrl: null
    });
  }

  submitAudioAnswer(sessionId: number, questionId: number, audioBlob: Blob, codeAnswer?: string): Observable<SessionAnswerDto> {
    const formData = new FormData();
    formData.append('sessionId', String(sessionId));
    formData.append('questionId', String(questionId));
    formData.append('audio', audioBlob, 'answer.webm');
    if (codeAnswer && codeAnswer.trim()) {
      formData.append('codeAnswer', codeAnswer);
    }

    return this.http.post<SessionAnswerDto>(`${this.base}/submit-audio`, formData);
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }

    return '/interview-service/api/v1';
  }
}
