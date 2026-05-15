import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  AddBookmarkRequest,
  AnswerEvaluationDto,
  InterviewQuestionDto,
  InterviewReportDto,
  InterviewSessionDto,
  InterviewStreakDto,
  LiveBootstrapResponse,
  QuestionBookmarkDto,
  RetryAnswerRequest,
  SessionAnswerDto,
  SessionQuestionOrderDto,
  StartSessionRequest,
  StartLiveSessionRequest,
  StartLiveSessionResponse,
  SubmitAnswerRequest,
  SubmitFollowUpRequest,
  UpsertQuestionRequest,
} from './interview.models';

@Injectable({
  providedIn: 'root',
})
export class InterviewApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = this.resolveBaseUrl();
  private readonly backendOrigin = this.resolveOrigin(this.baseUrl);
  private readonly interviewServiceBase = this.baseUrl.replace(/\/api\/v1\/?$/, '');
  private readonly adminInterviewBaseUrl = `${this.baseUrl}/admin/interview`;

  resolveBackendAssetUrl(value: string): string {
    if (!value) {
      return '';
    }

    if (value.startsWith('/api/v1/')) {
      return this.resolveInterviewAssetPath(value);
    }

    if (value.startsWith('/interview-service/api/v1/')) {
      return this.resolveInterviewAssetPath(value);
    }

    if (/^https?:\/\//i.test(value)) {
      try {
        const parsed = new URL(value);
        if (parsed.pathname.startsWith('/interview-service/api/v1/')) {
          return value;
        }

        if (parsed.pathname.startsWith('/api/v1/')) {
          return this.resolveInterviewAssetPath(parsed.pathname);
        }
      } catch {
        // Keep original URL when parsing fails.
      }

      return value;
    }

    const normalizedValue = value.startsWith('/') ? value : `/${value}`;
    if (normalizedValue.startsWith('/api/v1/')) {
      return this.resolveInterviewAssetPath(normalizedValue);
    }

    if (normalizedValue.startsWith('/interview-service/api/v1/')) {
      return this.resolveInterviewAssetPath(normalizedValue);
    }

    return `${this.backendOrigin}${value.startsWith('/') ? value : `/${value}`}`;
  }

  getStreak(userId: number): Observable<InterviewStreakDto> {
    return this.http.get<InterviewStreakDto>(`${this.baseUrl}/streaks/user/${userId}`);
  }

  getSessionsByUser(userId: number): Observable<InterviewSessionDto[]> {
    return this.http.get<InterviewSessionDto[]>(`${this.baseUrl}/sessions/user/${userId}`);
  }

  getActiveSession(userId: number): Observable<InterviewSessionDto | null> {
    return this.http.get<InterviewSessionDto | null>(`${this.baseUrl}/sessions/user/${userId}/active`);
  }

  startSession(payload: StartSessionRequest): Observable<InterviewSessionDto> {
    const body = {
      ...payload,
      roleType: payload.role,
    };

    return this.http.post<InterviewSessionDto>(`${this.baseUrl}/sessions/start`, body).pipe(
      catchError((error: HttpErrorResponse) => {
        const shouldRetryWithQueryParams =
          error.status === 0 ||
          error.status === 400 ||
          error.status === 404 ||
          error.status === 405 ||
          error.status === 415 ||
          error.status === 422 ||
          error.status >= 500;

        if (!shouldRetryWithQueryParams) {
          return throwError(() => error);
        }

        const params = new HttpParams()
          .set('userId', String(payload.userId))
          .set('careerPathId', String(payload.careerPathId))
          .set('role', payload.role)
          .set('mode', payload.mode)
          .set('type', payload.type)
          .set('questionCount', String(payload.questionCount));

        return this.http.post<InterviewSessionDto>(`${this.baseUrl}/sessions/start`, null, { params }).pipe(
          catchError(() => throwError(() => error))
        );
      })
    );
  }

  startLiveSession(payload: StartLiveSessionRequest): Observable<StartLiveSessionResponse> {
    return this.http.post<StartLiveSessionResponse>(`${this.baseUrl}/sessions/start-live`, payload);
  }

  getLiveBootstrap(
    sessionId: number,
    params?: { companyName?: string; targetRole?: string; candidateName?: string }
  ): Observable<LiveBootstrapResponse> {
    let queryParams = new HttpParams();
    if (params?.companyName) {
      queryParams = queryParams.set('companyName', params.companyName);
    }
    if (params?.targetRole) {
      queryParams = queryParams.set('targetRole', params.targetRole);
    }
    if (params?.candidateName) {
      queryParams = queryParams.set('candidateName', params.candidateName);
    }

    return this.http.get<LiveBootstrapResponse>(`${this.baseUrl}/sessions/${sessionId}/live-bootstrap`, {
      params: queryParams,
    });
  }

  getSessionById(sessionId: number): Observable<InterviewSessionDto> {
    return this.http.get<InterviewSessionDto>(`${this.baseUrl}/sessions/${sessionId}`);
  }

  pauseSession(sessionId: number): Observable<InterviewSessionDto> {
    return this.http.put<InterviewSessionDto>(`${this.baseUrl}/sessions/${sessionId}/pause`, {});
  }

  resumeSession(sessionId: number): Observable<InterviewSessionDto> {
    return this.http.put<InterviewSessionDto>(`${this.baseUrl}/sessions/${sessionId}/resume`, {});
  }

  completeSession(sessionId: number): Observable<InterviewSessionDto> {
    return this.http.put<InterviewSessionDto>(`${this.baseUrl}/sessions/${sessionId}/complete`, {});
  }

  abandonSession(sessionId: number): Observable<InterviewSessionDto> {
    return this.http.put<InterviewSessionDto>(`${this.baseUrl}/sessions/${sessionId}/abandon`, {});
  }

  getSessionQuestionOrder(sessionId: number): Observable<SessionQuestionOrderDto[]> {
    return this.http.get<SessionQuestionOrderDto[]>(`${this.baseUrl}/sessions/${sessionId}/questions`);
  }

  getCurrentSessionQuestion(sessionId: number): Observable<InterviewQuestionDto> {
    return this.http.get<InterviewQuestionDto>(`${this.baseUrl}/sessions/${sessionId}/questions/current`);
  }

  getNextSessionQuestion(sessionId: number): Observable<InterviewQuestionDto | null> {
    return this.http
      .get<InterviewQuestionDto>(`${this.baseUrl}/sessions/${sessionId}/questions/next`, { observe: 'response' })
      .pipe(
        map((response: HttpResponse<InterviewQuestionDto>) => response.body ?? null),
        catchError((error: HttpErrorResponse) => {
          if (error.status === 204 || error.status === 404) {
            return of(null);
          }

          return throwError(() => error);
        })
      );
  }

  submitAnswer(payload: SubmitAnswerRequest): Observable<SessionAnswerDto> {
    return this.http.post<SessionAnswerDto>(`${this.baseUrl}/answers/submit`, payload);
  }

  retryAnswer(payload: RetryAnswerRequest): Observable<SessionAnswerDto> {
    return this.http.post<SessionAnswerDto>(`${this.baseUrl}/answers/retry`, payload);
  }

  submitFollowUp(payload: SubmitFollowUpRequest): Observable<SessionAnswerDto> {
    return this.http.post<SessionAnswerDto>(`${this.baseUrl}/answers/follow-up`, payload);
  }

  triggerEvaluation(answerId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/evaluations/trigger/${answerId}`, {});
  }

  getEvaluationByAnswer(answerId: number): Observable<AnswerEvaluationDto> {
    return this.http.get<AnswerEvaluationDto>(`${this.baseUrl}/evaluations/answer/${answerId}`);
  }

  getEvaluationsBySession(sessionId: number): Observable<AnswerEvaluationDto[]> {
    return this.http.get<AnswerEvaluationDto[]>(`${this.baseUrl}/evaluations/session/${sessionId}`);
  }

  getAnswersBySession(sessionId: number): Observable<SessionAnswerDto[]> {
    return this.http.get<SessionAnswerDto[]>(`${this.baseUrl}/answers/session/${sessionId}`);
  }

  generateReport(sessionId: number): Observable<InterviewReportDto> {
    return this.http.post<InterviewReportDto>(`${this.baseUrl}/reports/generate/${sessionId}`, {});
  }

  getReportById(reportId: number): Observable<InterviewReportDto> {
    return this.http.get<InterviewReportDto>(`${this.baseUrl}/reports/${reportId}`);
  }

  getReportBySession(sessionId: number): Observable<InterviewReportDto> {
    return this.http.get<InterviewReportDto>(`${this.baseUrl}/reports/session/${sessionId}`);
  }

  getReportsByUser(userId: number): Observable<InterviewReportDto[]> {
    return this.http.get<InterviewReportDto[]>(`${this.baseUrl}/reports/user/${userId}`);
  }

  getReportPdfUrl(reportId: number): Observable<string> {
    return this.http.get(`${this.baseUrl}/reports/${reportId}/pdf`, { responseType: 'text' });
  }

  getLeaderboard(limit = 10): Observable<InterviewStreakDto[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<InterviewStreakDto[]>(`${this.baseUrl}/streaks/leaderboard`, { params });
  }

  getBookmarksByUser(userId: number): Observable<QuestionBookmarkDto[]> {
    return this.http.get<QuestionBookmarkDto[]>(`${this.baseUrl}/bookmarks/user/${userId}`);
  }

  getBookmarkTags(userId: number): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/bookmarks/user/${userId}/tags`);
  }

  addBookmark(payload: AddBookmarkRequest): Observable<QuestionBookmarkDto> {
    return this.http.post<QuestionBookmarkDto>(`${this.baseUrl}/bookmarks`, payload);
  }

  updateBookmarkNote(bookmarkId: number, note: string): Observable<QuestionBookmarkDto> {
    return this.http.put<QuestionBookmarkDto>(`${this.baseUrl}/bookmarks/${bookmarkId}/note`, { note });
  }

  removeBookmark(userId: number, questionId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/bookmarks/user/${userId}/question/${questionId}`);
  }

  getQuestions(params?: {
    role?: string;
    type?: string;
    difficulty?: string;
  }): Observable<InterviewQuestionDto[]> {
    if (!params?.role && !params?.type && !params?.difficulty) {
      return this.http.get<InterviewQuestionDto[]>(`${this.baseUrl}/questions`);
    }

    let httpParams = new HttpParams();
    if (params.role) {
      httpParams = httpParams.set('role', params.role);
    }
    if (params.type) {
      httpParams = httpParams.set('type', params.type);
    }
    if (params.difficulty) {
      httpParams = httpParams.set('difficulty', params.difficulty);
    }

    return this.http.get<InterviewQuestionDto[]>(`${this.baseUrl}/questions/filter`, { params: httpParams });
  }

  getQuestionCoverage(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.baseUrl}/questions/coverage`);
  }

  createQuestion(payload: UpsertQuestionRequest): Observable<InterviewQuestionDto> {
    return this.http.post<InterviewQuestionDto>(`${this.baseUrl}/questions`, payload);
  }

  updateQuestion(questionId: number, payload: UpsertQuestionRequest): Observable<InterviewQuestionDto> {
    return this.http.put<InterviewQuestionDto>(`${this.baseUrl}/questions/${questionId}`, payload);
  }

  deleteQuestion(questionId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/questions/${questionId}`);
  }

  addQuestionTag(questionId: number, tag: string): Observable<InterviewQuestionDto> {
    const params = new HttpParams().set('tag', tag);
    return this.http.post<InterviewQuestionDto>(`${this.baseUrl}/questions/${questionId}/tags`, null, { params });
  }

  getAdminOverviewStats(): Observable<any> {
    return this.http.get<any>(`${this.adminInterviewBaseUrl}/stats/overview`);
  }

  getAdminSessionsOverTime(days = 30): Observable<any[]> {
    const params = new HttpParams().set('days', String(days));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/sessions-over-time`, { params });
  }

  getAdminScoresOverTime(days = 30): Observable<any[]> {
    const params = new HttpParams().set('days', String(days));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/scores-over-time`, { params });
  }

  getAdminByRole(): Observable<any[]> {
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/by-role`);
  }

  getAdminByType(): Observable<any[]> {
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/by-type`);
  }

  getAdminByDifficulty(): Observable<any[]> {
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/by-difficulty`);
  }

  getAdminScoreDistribution(): Observable<any[]> {
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/score-distribution`);
  }

  getAdminMostAsked(limit = 10): Observable<any[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/questions/most-asked`, { params });
  }

  getAdminHardest(limit = 10): Observable<any[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/questions/hardest`, { params });
  }

  getAdminLongestAnswers(limit = 10): Observable<any[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/questions/longest-answers`, { params });
  }

  getAdminBestPerforming(limit = 10): Observable<any[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/questions/best-performing`, { params });
  }

  getAdminLeaderboard(limit = 10): Observable<any[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/leaderboard`, { params });
  }

  getAdminTopStreaks(limit = 10): Observable<any[]> {
    const params = new HttpParams().set('limit', String(limit));
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/stats/streaks/top`, { params });
  }

  getAdminQuestions(params?: {
    role?: string;
    type?: string;
    difficulty?: string;
    active?: string;
    search?: string;
    page?: number;
    size?: number;
  }): Observable<any> {
    let httpParams = new HttpParams();
    if (params?.role) {
      httpParams = httpParams.set('role', params.role);
    }
    if (params?.type) {
      httpParams = httpParams.set('type', params.type);
    }
    if (params?.difficulty) {
      httpParams = httpParams.set('difficulty', params.difficulty);
    }
    if (params?.active) {
      httpParams = httpParams.set('active', params.active);
    }
    if (params?.search) {
      httpParams = httpParams.set('search', params.search);
    }
    if (params?.page !== undefined) {
      httpParams = httpParams.set('page', String(params.page));
    }
    if (params?.size !== undefined) {
      httpParams = httpParams.set('size', String(params.size));
    }

    return this.http.get<any>(`${this.adminInterviewBaseUrl}/questions`, { params: httpParams });
  }

  getAdminQuestionById(id: number): Observable<InterviewQuestionDto> {
    return this.http.get<InterviewQuestionDto>(`${this.adminInterviewBaseUrl}/questions/${id}`);
  }

  createAdminQuestion(payload: any): Observable<InterviewQuestionDto> {
    return this.http.post<InterviewQuestionDto>(`${this.adminInterviewBaseUrl}/questions`, payload);
  }

  updateAdminQuestion(id: number, payload: any): Observable<InterviewQuestionDto> {
    return this.http.put<InterviewQuestionDto>(`${this.adminInterviewBaseUrl}/questions/${id}`, payload);
  }

  deleteAdminQuestion(id: number): Observable<void> {
    return this.http.delete<void>(`${this.adminInterviewBaseUrl}/questions/${id}`);
  }

  toggleAdminQuestionActive(id: number): Observable<InterviewQuestionDto> {
    return this.http.patch<InterviewQuestionDto>(`${this.adminInterviewBaseUrl}/questions/${id}/toggle-active`, {});
  }

  getAdminCoverage(): Observable<any[]> {
    return this.http.get<any[]>(`${this.adminInterviewBaseUrl}/questions/coverage`);
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }


    return '/interview-service/api/v1';
  }

  private resolveOrigin(urlLike: string): string {
    try {
      if (/^https?:\/\//i.test(urlLike)) {
        return new URL(urlLike).origin;
      }

      if (globalThis.location?.origin) {
        return new URL(urlLike, globalThis.location.origin).origin;
      }

      return '';
    } catch {
      return globalThis.location?.origin ?? '';
    }
  }

  private resolveInterviewAssetPath(value: string): string {
    const normalizedValue = value.startsWith('/') ? value : `/${value}`;
    const withoutContext = normalizedValue.startsWith('/interview-service/api/v1/')
      ? normalizedValue.replace('/interview-service/api/v1/', '/api/v1/')
      : normalizedValue;

    if (withoutContext.startsWith('/api/v1/')) {
      if (this.interviewServiceBase) {
        return `${this.interviewServiceBase}${withoutContext}`;
      }

      if (this.backendOrigin) {
        return `${this.backendOrigin}/interview-service${withoutContext}`;
      }
    }

    if (this.backendOrigin && normalizedValue.startsWith('/interview-service/api/v1/')) {
      return `${this.backendOrigin}${normalizedValue}`;
    }

    return normalizedValue;
  }
}
