import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { assessmentApiBaseUrl } from '../../../core/assessment-api-url';

export interface CategoryAdminRow {
  id: number;
  code: string;
  title: string;
  description: string | null;
  questionCount: number;
}

/** Result of POST /admin/seed-default-bank */
export interface SeedDefaultBankResult {
  added: number;
  totalCategories: number;
}

export interface ChoiceAdminRow {
  id: number;
  label: string;
  correct: boolean;
  sortOrder: number;
}

export interface PendingAssignmentRow {
  userId: string;
  situation: string | null;
  careerPath: string | null;
  status: string;
  createdAt: string | null;
}

/** Completed MCQ session waiting for admin to publish the score to the candidate. */
export interface AssessmentSessionAdminRow {
  id: number;
  userId: string;
  categoryId: number;
  categoryTitle: string;
  categoryCode?: string;
  topicTag: string | null;
  startedAt: string | null;
  completedAt: string | null;
  status: string;
  scorePercent: number | null;
  scoreReleased: boolean;
  /** Same as scoreReleased (backend). */
  isPublished?: boolean;
  notes: string | null;
  /** Candidate-facing message after publish (optional). */
  adminFeedback: string | null;
}

/** One row in GET /admin/sessions/{id}/review */
export interface AnswerReviewAdminRow {
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

export interface SessionResultAdminDto {
  session: AssessmentSessionAdminRow;
  answers: AnswerReviewAdminRow[];
}

export interface QuestionAdminRow {
  id: number;
  categoryId: number;
  prompt: string;
  points: number;
  difficulty: string;
  active: boolean;
  /** Tag for topic-based quizzes (e.g. java). */
  topic?: string | null;
  choices: ChoiceAdminRow[];
}

@Injectable({ providedIn: 'root' })
export class AssessmentAdminApiService {
  private readonly http = inject(HttpClient);

  private base(): string {
    return assessmentApiBaseUrl();
  }

  private adminHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'X-Admin-Api-Key': environment.assessmentAdminApiKey,
    });
  }

  listCategories(): Observable<CategoryAdminRow[]> {
    return this.http.get<CategoryAdminRow[]>(`${this.base()}/admin/categories`, {
      headers: this.adminHeaders(),
    });
  }

  /** Inserts missing seeded categories (same as startup). Safe to call multiple times. */
  seedDefaultBank(): Observable<SeedDefaultBankResult> {
    return this.http.post<SeedDefaultBankResult>(`${this.base()}/admin/seed-default-bank`, {}, {
      headers: this.adminHeaders(),
    });
  }

  createCategory(body: { code: string; title: string; description?: string | null }): Observable<CategoryAdminRow> {
    return this.http.post<CategoryAdminRow>(`${this.base()}/admin/categories`, body, {
      headers: this.adminHeaders(),
    });
  }

  updateCategory(
    id: number,
    body: { code: string; title: string; description?: string | null }
  ): Observable<CategoryAdminRow> {
    return this.http.put<CategoryAdminRow>(`${this.base()}/admin/categories/${id}`, body, {
      headers: this.adminHeaders(),
    });
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/admin/categories/${id}`, {
      headers: this.adminHeaders(),
    });
  }

  listQuestions(categoryId: number): Observable<QuestionAdminRow[]> {
    return this.http.get<QuestionAdminRow[]>(`${this.base()}/admin/categories/${categoryId}/questions`, {
      headers: this.adminHeaders(),
    });
  }

  createQuestion(
    categoryId: number,
    body: { prompt: string; points: number; difficulty: string; active: boolean; topic?: string | null }
  ): Observable<QuestionAdminRow> {
    return this.http.post<QuestionAdminRow>(
      `${this.base()}/admin/categories/${categoryId}/questions`,
      body,
      { headers: this.adminHeaders() }
    );
  }

  updateQuestion(
    questionId: number,
    body: { prompt: string; points: number; difficulty: string; active: boolean; topic?: string | null }
  ): Observable<QuestionAdminRow> {
    return this.http.put<QuestionAdminRow>(`${this.base()}/admin/questions/${questionId}`, body, {
      headers: this.adminHeaders(),
    });
  }

  deleteQuestion(questionId: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/admin/questions/${questionId}`, {
      headers: this.adminHeaders(),
    });
  }

  createChoice(
    questionId: number,
    body: { label: string; correct: boolean; sortOrder: number }
  ): Observable<ChoiceAdminRow> {
    return this.http.post<ChoiceAdminRow>(
      `${this.base()}/admin/questions/${questionId}/choices`,
      body,
      { headers: this.adminHeaders() }
    );
  }

  updateChoice(
    choiceId: number,
    body: { label: string; correct: boolean; sortOrder: number }
  ): Observable<ChoiceAdminRow> {
    return this.http.put<ChoiceAdminRow>(`${this.base()}/admin/choices/${choiceId}`, body, {
      headers: this.adminHeaders(),
    });
  }

  deleteChoice(choiceId: number): Observable<void> {
    return this.http.delete<void>(`${this.base()}/admin/choices/${choiceId}`, {
      headers: this.adminHeaders(),
    });
  }

  listPendingAssignments(): Observable<PendingAssignmentRow[]> {
    return this.http.get<PendingAssignmentRow[]>(`${this.base()}/admin/assignments/pending`, {
      headers: this.adminHeaders(),
    });
  }

  approveAssignment(userId: string, categoryIds: number[]): Observable<unknown> {
    return this.http.post(`${this.base()}/admin/assignments/${userId}/approve`, { categoryIds }, {
      headers: this.adminHeaders(),
    });
  }

  listSessionsPendingRelease(): Observable<AssessmentSessionAdminRow[]> {
    return this.http.get<AssessmentSessionAdminRow[]>(`${this.base()}/admin/sessions/pending-release`, {
      headers: this.adminHeaders(),
    });
  }

  /** All completed attempts (newest first) — open review per session id. */
  listAllCompletedSessions(): Observable<AssessmentSessionAdminRow[]> {
    return this.http.get<AssessmentSessionAdminRow[]>(`${this.base()}/admin/sessions/completed`, {
      headers: this.adminHeaders(),
    });
  }

  /** Full candidate responses for a completed session (any state — pending release or already published). */
  getSessionReview(sessionId: number): Observable<SessionResultAdminDto> {
    return this.http.get<SessionResultAdminDto>(`${this.base()}/admin/sessions/${sessionId}/review`, {
      headers: this.adminHeaders(),
    });
  }

  releaseSessionResult(
    sessionId: number,
    options?: { adminNote?: string | null; feedbackToCandidate?: string | null }
  ): Observable<AssessmentSessionAdminRow> {
    const body: { adminNote?: string; feedbackToCandidate?: string } = {};
    const n = options?.adminNote?.trim();
    const f = options?.feedbackToCandidate?.trim();
    if (n) body.adminNote = n;
    if (f) body.feedbackToCandidate = f;
    return this.http.post<AssessmentSessionAdminRow>(
      `${this.base()}/admin/sessions/${sessionId}/release-result`,
      body,
      { headers: this.adminHeaders() }
    );
  }
}
