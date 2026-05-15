import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { LUCIDE_ICONS } from '../../../shared/lucide-icons';
import { getAssessmentUserId } from '../profile/profile-user-id';
import { canonicalSessionListUserId, collectCandidateUserIdsForSessions } from './assessment-canonical-user';
import {
  CandidateAssignmentApiService,
  CandidateAssignmentStatusDto,
} from './candidate-assignment-api.service';
import {
  CandidateSessionApiService,
  isSessionPublished,
  SessionResponseDto,
} from './candidate-session-api.service';

@Component({
  selector: 'app-assessment-hub',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './assessment-hub.component.html',
  styleUrl: './assessment-hub.component.scss',
})
export class AssessmentHubComponent implements OnInit {
  private readonly assignmentApi = inject(CandidateAssignmentApiService);
  private readonly sessionApi = inject(CandidateSessionApiService);
  private readonly router = inject(Router);

  loading = signal(true);
  errorMsg = signal<string | null>(null);

  /** No row in MS-Assessment — legacy accounts can still start sessions if backend allows. */
  noPlan = signal(false);

  plan = signal<CandidateAssignmentStatusDto | null>(null);
  history = signal<SessionResponseDto[]>([]);

  startingCategoryId = signal<number | null>(null);

  ngOnInit(): void {
    const baseUid = getAssessmentUserId();
    if (!baseUid) {
      this.loading.set(false);
      this.errorMsg.set('Sign in to see your assessments.');
      return;
    }

    this.assignmentApi
      .getStatus(baseUid)
      .pipe(
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 404) {
            return of(null as CandidateAssignmentStatusDto | null);
          }
          return throwError(() => err);
        }),
        switchMap((plan) => {
          const ids = collectCandidateUserIdsForSessions(plan, baseUid);
          return this.sessionApi.listForUserMergedDistinct(ids).pipe(
            catchError(() => {
              this.errorMsg.set(
                'Could not load your attempts from the assessment server. Check MS-Assessment (port 8084) and refresh.'
              );
              return of([] as SessionResponseDto[]);
            }),
            map((history) => ({ plan, history }))
          );
        })
      )
      .subscribe({
        next: ({ plan, history }) => {
          if (plan === null) {
            this.noPlan.set(true);
            this.plan.set(null);
          } else {
            this.noPlan.set(false);
            this.plan.set(plan);
          }
          this.history.set(history);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.errorMsg.set(this.formatErr(err));
        },
      });
  }

  refresh(): void {
    this.loading.set(true);
    this.errorMsg.set(null);
    this.ngOnInit();
  }

  /**
   * One completed attempt per category blocks a new Start; an in-progress session shows Continue instead.
   */
  categoryAction(categoryId: number): {
    kind: 'start' | 'continue' | 'completed';
    session?: SessionResponseDto;
  } {
    const cid = Number(categoryId);
    const list = this.history().filter((s) => Number(s.categoryId) === cid);
    const completed = list.find((s) => this.sessionCompleted(s));
    if (completed) {
      return { kind: 'completed', session: completed };
    }
    const inProg = list.find((s) => this.sessionInProgress(s));
    if (inProg) {
      return { kind: 'continue', session: inProg };
    }
    return { kind: 'start' };
  }

  continueSession(sessionId: number): void {
    void this.router.navigate(['/dashboard/assessments/session', sessionId]);
  }

  startCategory(categoryId: number): void {
    const baseUid = getAssessmentUserId();
    if (!baseUid) return;
    const uid = canonicalSessionListUserId(this.plan(), baseUid);
    this.startingCategoryId.set(categoryId);
    this.sessionApi.startSession(uid, categoryId).subscribe({
      next: (s) => {
        this.startingCategoryId.set(null);
        void this.router.navigate(['/dashboard/assessments/session', s.id]);
      },
      error: (err: unknown) => {
        this.startingCategoryId.set(null);
        this.errorMsg.set(this.formatErr(err));
      },
    });
  }

  private sessionCompleted(s: SessionResponseDto): boolean {
    return String(s.status ?? '')
      .trim()
      .toUpperCase()
      .replace(/-/g, '_') === 'COMPLETED';
  }

  private sessionInProgress(s: SessionResponseDto): boolean {
    return String(s.status ?? '')
      .trim()
      .toUpperCase()
      .replace(/-/g, '_') === 'IN_PROGRESS';
  }

  /** Session row is completed (submitted); status stays COMPLETED after admin publish. */
  sessionIsCompleted(s: SessionResponseDto): boolean {
    return this.sessionCompleted(s);
  }

  /** Results/score/feedback visible only after admin publish. */
  sessionIsPublished(s: SessionResponseDto): boolean {
    return isSessionPublished(s);
  }

  private formatErr(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const b = err.error;
      if (typeof b === 'string' && b.trim()) return b;
      if (b && typeof b === 'object' && 'message' in b) {
        return String((b as { message: unknown }).message);
      }
      if (err.status === 403) {
        return 'You already have an attempt for this category. Refresh the page to continue or view results.';
      }
      return err.message || `Error ${err.status}`;
    }
    return 'Something went wrong.';
  }
}
