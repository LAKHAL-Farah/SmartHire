import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { assessmentApiBaseUrl } from '../../../core/assessment-api-url';

export interface ChoiceViewDto {
  id: number;
  label: string;
}

export interface QuestionPaperItemDto {
  id: number;
  prompt: string;
  difficulty: string;
  points: number;
  choices: ChoiceViewDto[];
}

export interface QuestionPaperResponseDto {
  sessionId: number;
  categoryId: number;
  categoryTitle: string;
  questions: QuestionPaperItemDto[];
}

export interface SessionResponseDto {
  id: number;
  userId: string;
  categoryId: number;
  categoryTitle: string;
  /** Bank code for this category (e.g. JAVA_OOP). */
  categoryCode?: string;
  topicTag: string | null;
  startedAt: string;
  completedAt: string | null;
  status: string;
  scorePercent: number | null;
  /** When false and completed, candidate must not see score until admin publishes. */
  scoreReleased: boolean;
  /** Same as scoreReleased (backend publishes both). */
  isPublished?: boolean;
  notes: string | null;
  /** Shown to the candidate after the admin publishes results (optional). */
  adminFeedback: string | null;
}

export interface AnswerReviewItemDto {
  questionId: number;
  prompt: string;
  difficulty: string;
  questionPoints: number;
  selectedChoiceId: number;
  selectedLabel: string;
  correctChoiceId: number;
  correctLabel: string;
  correct: boolean;
  pointsEarned: number;
}

export interface SessionResultResponseDto {
  session: SessionResponseDto;
  answers: AnswerReviewItemDto[];
}

export function isSessionCompleted(s: SessionResponseDto): boolean {
  return String(s.status ?? '')
    .trim()
    .toUpperCase()
    .replace(/-/g, '_') === 'COMPLETED';
}

/** Score and feedback are visible only after admin publish (result released to candidate). */
export function isSessionPublished(s: SessionResponseDto): boolean {
  if (s.scoreReleased === true || s.isPublished === true) return true;
  if (s.scoreReleased === false && s.isPublished === false) return false;
  /* Backend omits booleans in some clients; unpublished completed attempts have null score. */
  return isSessionCompleted(s) && s.scorePercent != null;
}

function mergeSessionsById(arrays: SessionResponseDto[][]): SessionResponseDto[] {
  const byId = new Map<number, SessionResponseDto>();
  for (const row of arrays.flat()) {
    if (!byId.has(row.id)) {
      byId.set(row.id, row);
    }
  }
  return [...byId.values()].sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
  );
}

@Injectable({ providedIn: 'root' })
export class CandidateSessionApiService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return assessmentApiBaseUrl();
  }

  startSession(userId: string, categoryId: number): Observable<SessionResponseDto> {
    return this.http.post<SessionResponseDto>(`${this.base()}/sessions`, { userId, categoryId });
  }

  getPaper(sessionId: number): Observable<QuestionPaperResponseDto> {
    return this.http.get<QuestionPaperResponseDto>(`${this.base()}/sessions/${sessionId}/paper`);
  }

  submit(
    sessionId: number,
    selections: { questionId: number; answerChoiceId: number }[],
    notes?: string | null
  ): Observable<SessionResponseDto> {
    return this.http.post<SessionResponseDto>(`${this.base()}/sessions/${sessionId}/submit`, {
      selections,
      notes: notes ?? null,
    });
  }

  listForUser(userId: string): Observable<SessionResponseDto[]> {
    return this.http.get<SessionResponseDto[]>(`${this.base()}/sessions/user/${userId}`);
  }

  /**
   * Loads sessions for every distinct candidate id (profile vs auth vs assignment row) and merges by session id.
   * Fixes empty hub history when attempts were stored under a different UUID than the one used for GET /sessions/user.
   */
  listForUserMergedDistinct(userIds: string[]): Observable<SessionResponseDto[]> {
    const uniq: string[] = [];
    const seen = new Set<string>();
    for (const raw of userIds) {
      const t = raw?.trim();
      if (!t || !/^[0-9a-f-]{36}$/i.test(t)) continue;
      const k = t.toLowerCase();
      if (seen.has(k)) continue;
      seen.add(k);
      uniq.push(t);
    }
    if (uniq.length === 0) {
      return of([]);
    }
    if (uniq.length === 1) {
      return this.listForUser(uniq[0]).pipe(catchError(() => of([])));
    }
    return forkJoin(
      uniq.map((id) =>
        this.listForUser(id).pipe(catchError(() => of([] as SessionResponseDto[])))
      )
    ).pipe(map((arrays) => mergeSessionsById(arrays)));
  }

  /** Full review after admin published results. Pass userId so the API can verify ownership. */
  getReview(sessionId: number, userId: string): Observable<SessionResultResponseDto> {
    const params = new HttpParams().set('userId', userId.trim());
    return this.http.get<SessionResultResponseDto>(`${this.base()}/sessions/${sessionId}/review`, { params });
  }
}
