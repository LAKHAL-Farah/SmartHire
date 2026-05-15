import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import {
  CandidateSessionApiService,
  SessionResultResponseDto,
} from './candidate-session-api.service';
import { CandidateAssignmentApiService } from './candidate-assignment-api.service';
import { canonicalSessionListUserId } from './assessment-canonical-user';
import { getAssessmentUserId } from '../profile/profile-user-id';
import { LUCIDE_ICONS } from '../../../shared/lucide-icons';

@Component({
  selector: 'app-assessment-review',
  standalone: true,
  imports: [CommonModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './assessment-review.component.html',
  styleUrl: './assessment-review.component.scss',
})
export class AssessmentReviewComponent implements OnInit {
  private readonly api = inject(CandidateSessionApiService);
  private readonly assignmentApi = inject(CandidateAssignmentApiService);
  private readonly route = inject(ActivatedRoute);

  loading = signal(true);
  errorMsg = signal<string | null>(null);
  result = signal<SessionResultResponseDto | null>(null);

  ngOnInit(): void {
    const sid = Number(this.route.snapshot.paramMap.get('sessionId'));
    const qp = this.route.snapshot.queryParamMap.get('userId')?.trim();
    const baseUid = getAssessmentUserId();
    /** Session owner from tile link — required when storage ids diverge from DB. */
    const seed = qp && /^[0-9a-f-]{36}$/i.test(qp) ? qp : baseUid;
    if (!Number.isFinite(sid) || !seed) {
      this.loading.set(false);
      this.errorMsg.set(!seed ? 'Sign in to view your results.' : 'Invalid session.');
      return;
    }
    this.assignmentApi
      .getStatus(seed)
      .pipe(
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 404) {
            return of(null);
          }
          return throwError(() => err);
        }),
        switchMap((plan) => {
          const uid = canonicalSessionListUserId(plan, seed);
          return this.api.getReview(sid, uid);
        })
      )
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.errorMsg.set(this.formatErr(err));
        },
      });
  }

  private formatErr(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const b = err.error;
      if (typeof b === 'string' && b.trim()) return b;
      if (b && typeof b === 'object' && 'message' in b) {
        return String((b as { message: unknown }).message);
      }
      return err.message || `Error ${err.status}`;
    }
    return 'Something went wrong.';
  }
}
