import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../shared/lucide-icons';
import {
  CandidateSessionApiService,
  QuestionPaperItemDto,
  QuestionPaperResponseDto,
} from './candidate-session-api.service';

@Component({
  selector: 'app-mcq-session',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LUCIDE_ICONS],
  templateUrl: './mcq-session.component.html',
  styleUrl: './mcq-session.component.scss',
})
export class McqSessionComponent implements OnInit {
  private readonly api = inject(CandidateSessionApiService);
  private readonly route = inject(ActivatedRoute);

  loading = signal(true);
  errorMsg = signal<string | null>(null);

  paper = signal<QuestionPaperResponseDto | null>(null);
  /** questionId -> choiceId */
  picks = signal<Record<number, number>>({});

  submitted = signal(false);
  scorePercent = signal<number | null>(null);
  sessionId = signal<number | null>(null);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('sessionId');
    const sid = idParam ? Number(idParam) : NaN;
    if (!Number.isFinite(sid)) {
      this.loading.set(false);
      this.errorMsg.set('Invalid session.');
      return;
    }
    this.sessionId.set(sid);
    this.api.getPaper(sid).subscribe({
      next: (p) => {
        this.paper.set(p);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMsg.set(this.formatErr(err));
      },
    });
  }

  selectChoice(questionId: number, choiceId: number): void {
    this.picks.update((m) => ({ ...m, [questionId]: choiceId }));
  }

  canSubmit(paper: QuestionPaperResponseDto): boolean {
    const m = this.picks();
    return paper.questions.every((q) => m[q.id] != null);
  }

  submit(): void {
    const p = this.paper();
    const sid = this.sessionId();
    if (!p || sid == null) return;
    if (!this.canSubmit(p)) {
      this.errorMsg.set('Answer every question before submitting.');
      return;
    }
    this.errorMsg.set(null);
    const selections = p.questions.map((q) => ({
      questionId: q.id,
      answerChoiceId: this.picks()[q.id]!,
    }));
    this.loading.set(true);
    this.api.submit(sid, selections).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.submitted.set(true);
        this.scorePercent.set(res.scorePercent ?? null);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.errorMsg.set(this.formatErr(err));
      },
    });
  }

  trackByQ(_i: number, q: QuestionPaperItemDto): number {
    return q.id;
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
