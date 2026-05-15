import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LiveSessionReadyPayload, LiveSessionStartRequest, LiveSessionStartResponse } from '../models/live-session.model';
import { InterviewReportDto } from '../../features/front-office/dashboard/interview/interview.models';

@Injectable({ providedIn: 'root' })
export class LiveSessionService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = this.resolveBaseUrl();
  private readonly baseUrl = `${this.apiBaseUrl}/sessions`;

  startLiveSession(req: LiveSessionStartRequest): Observable<LiveSessionStartResponse> {
    return this.http.post<LiveSessionStartResponse>(`${this.baseUrl}/start-live`, req);
  }

  abandonSession(sessionId: number): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${sessionId}/abandon`, {});
  }

  getLiveBootstrap(
    sessionId: number,
    params?: { companyName?: string; targetRole?: string; candidateName?: string }
  ): Observable<LiveSessionReadyPayload> {
    const query = new URLSearchParams();
    if (params?.companyName) {
      query.set('companyName', params.companyName);
    }
    if (params?.targetRole) {
      query.set('targetRole', params.targetRole);
    }
    if (params?.candidateName) {
      query.set('candidateName', params.candidateName);
    }

    const suffix = query.toString();
    const url = `${this.baseUrl}/${sessionId}/live-bootstrap${suffix ? `?${suffix}` : ''}`;
    return this.http.get<LiveSessionReadyPayload>(url);
  }

  getReportBySession(sessionId: number): Observable<InterviewReportDto> {
    return this.http.get<InterviewReportDto>(`${this.apiBaseUrl}/reports/session/${sessionId}`);
  }

  getReport(sessionId: number): Observable<InterviewReportDto> {
    return this.http.get<InterviewReportDto>(`${this.apiBaseUrl}/reports/session/${sessionId}`);
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }

    return '/api/v1';
  }
}
