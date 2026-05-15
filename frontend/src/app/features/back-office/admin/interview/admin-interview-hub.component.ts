import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { InterviewApiService } from '../../../front-office/dashboard/interview/interview-api.service';
import {
  DifficultyLevel,
  InterviewQuestionDto,
  InterviewReportDto,
  InterviewSessionDto,
  InterviewStreakDto,
  QuestionType,
  RoleType,
  SessionStatus,
  UpsertQuestionRequest,
} from '../../../front-office/dashboard/interview/interview.models';

type AdminTab = 'overview' | 'question-bank' | 'sessions' | 'reports';

interface QuestionFormModel {
  careerPathId: number;
  roleType: RoleType;
  questionText: string;
  type: QuestionType;
  difficulty: DifficultyLevel;
  category: string;
  domain: string;
  expectedPoints: string;
  hints: string;
  followUps: string;
  idealAnswer: string;
  tags: string;
  isActive: boolean;
}

@Component({
  selector: 'app-admin-interview-hub',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-interview-hub.component.html',
  styleUrl: './admin-interview-hub.component.scss',
})
export class AdminInterviewHubComponent implements OnInit {
  private readonly api = inject(InterviewApiService);
  private readonly router = inject(Router);

  readonly tab = signal<AdminTab>('overview');
  readonly error = signal<string | null>(null);

  readonly coverage = signal<Record<string, number>>({});
  readonly leaderboard = signal<InterviewStreakDto[]>([]);
  readonly overviewLoading = signal(false);

  readonly questionLoading = signal(false);
  readonly questions = signal<InterviewQuestionDto[]>([]);
  readonly questionSearch = signal('');
  readonly questionRole = signal<'ALL' | RoleType>('ALL');
  readonly questionType = signal<'ALL' | QuestionType>('ALL');
  readonly questionDifficulty = signal<'ALL' | DifficultyLevel>('ALL');
  readonly editingQuestionId = signal<number | null>(null);
  readonly questionSaving = signal(false);
  readonly questionFormError = signal<string | null>(null);

  readonly sessionsLoading = signal(false);
  readonly monitorUserInput = signal('');
  readonly monitorUserError = signal<string | null>(null);
  readonly sessions = signal<InterviewSessionDto[]>([]);
  readonly sessionStatusFilter = signal<'ALL' | SessionStatus>('ALL');

  readonly reportsLoading = signal(false);
  readonly reportsUserInput = signal('');
  readonly reportsUserError = signal<string | null>(null);
  readonly reports = signal<InterviewReportDto[]>([]);
  readonly reportSearch = signal('');

  readonly tagDialogQuestionId = signal<number | null>(null);
  readonly tagDraft = signal('');
  readonly tagError = signal<string | null>(null);
  readonly tagSaving = signal(false);

  readonly questionForm = signal<QuestionFormModel>(this.defaultQuestionForm());

  readonly totalCoverageCount = computed(() =>
    Object.values(this.coverage()).reduce((sum, value) => sum + Number(value || 0), 0)
  );

  readonly coverageEntries = computed(() =>
    Object.entries(this.coverage()).map(([key, value]) => ({ key, value }))
  );

  readonly activeQuestionsCount = computed(() => this.questions().filter((question) => question.active).length);

  readonly roleBreakdown = computed(() => {
    const stats: Record<RoleType, number> = { SE: 0, CLOUD: 0, AI: 0, ALL: 0 };
    for (const question of this.questions()) {
      stats[question.roleType] += 1;
    }
    return stats;
  });

  readonly filteredQuestions = computed(() => {
    const query = this.questionSearch().trim().toLowerCase();
    return this.questions().filter((question) => {
      if (this.questionRole() !== 'ALL' && question.roleType !== this.questionRole()) {
        return false;
      }
      if (this.questionType() !== 'ALL' && question.type !== this.questionType()) {
        return false;
      }
      if (this.questionDifficulty() !== 'ALL' && question.difficulty !== this.questionDifficulty()) {
        return false;
      }
      if (!query) {
        return true;
      }

      const haystack = [
        question.questionText,
        question.tags ?? '',
        question.category ?? '',
        question.domain ?? '',
      ]
        .join(' ')
        .toLowerCase();

      return haystack.includes(query);
    });
  });

  readonly filteredSessions = computed(() => {
    return this.sessions()
      .filter((session) => this.sessionStatusFilter() === 'ALL' || session.status === this.sessionStatusFilter())
      .sort((a, b) => this.dateValue(b.startedAt) - this.dateValue(a.startedAt));
  });

  readonly filteredReports = computed(() => {
    const query = this.reportSearch().trim().toLowerCase();
    return this.reports()
      .filter((report) => {
        if (!query) {
          return true;
        }

        const haystack = [
          `session ${report.sessionId}`,
          `score ${report.finalScore ?? ''}`,
          report.recruiterVerdict ?? '',
          report.recommendations ?? '',
        ]
          .join(' ')
          .toLowerCase();

        return haystack.includes(query);
      })
      .sort((a, b) => this.dateValue(b.generatedAt) - this.dateValue(a.generatedAt));
  });

  readonly isMonitorUserValid = computed(() => this.validateUserId(this.monitorUserInput()) === null);
  readonly isReportsUserValid = computed(() => this.validateUserId(this.reportsUserInput()) === null);

  ngOnInit(): void {
    this.loadOverview();
    this.loadQuestionBank();
  }

  setTab(tab: AdminTab): void {
    this.tab.set(tab);
  }

  loadOverview(): void {
    this.overviewLoading.set(true);
    this.error.set(null);

    forkJoin({
      coverage: this.api.getQuestionCoverage().pipe(catchError(() => of({}))),
      leaderboard: this.api.getLeaderboard(6).pipe(catchError(() => of([]))),
    }).subscribe({
      next: ({ coverage, leaderboard }) => {
        this.coverage.set(coverage);
        this.leaderboard.set(leaderboard);
        this.overviewLoading.set(false);
      },
      error: () => {
        this.error.set('Unable to load interview overview data.');
        this.overviewLoading.set(false);
      },
    });
  }

  loadQuestionBank(): void {
    this.questionLoading.set(true);
    this.api
      .getQuestions()
      .pipe(catchError(() => of([])))
      .subscribe((questions) => {
        this.questions.set(questions);
        this.questionLoading.set(false);
      });
  }

  beginCreateQuestion(): void {
    this.editingQuestionId.set(null);
    this.questionForm.set(this.defaultQuestionForm());
    this.questionFormError.set(null);
  }

  beginEditQuestion(question: InterviewQuestionDto): void {
    this.editingQuestionId.set(question.id);
    this.questionFormError.set(null);
    this.questionForm.set({
      careerPathId: question.careerPathId,
      roleType: question.roleType,
      questionText: question.questionText,
      type: question.type,
      difficulty: question.difficulty,
      category: question.category ?? '',
      domain: question.domain ?? '',
      expectedPoints: question.expectedPoints ?? '',
      hints: question.hints ?? '',
      followUps: question.followUps ?? '',
      idealAnswer: question.idealAnswer ?? '',
      tags: question.tags ?? '',
      isActive: question.active,
    });
  }

  saveQuestion(): void {
    if (this.questionSaving()) {
      return;
    }

    const formValidation = this.validateQuestionForm(this.questionForm());
    if (formValidation) {
      this.questionFormError.set(formValidation);
      return;
    }

    this.questionFormError.set(null);

    this.questionSaving.set(true);
    const form = this.questionForm();
    const payload: UpsertQuestionRequest = {
      careerPathId: form.careerPathId,
      roleType: form.roleType,
      questionText: form.questionText.trim(),
      type: form.type,
      difficulty: form.difficulty,
      category: form.category.trim() || null,
      domain: form.domain.trim() || null,
      expectedPoints: form.expectedPoints.trim() || null,
      hints: form.hints.trim() || null,
      followUps: form.followUps.trim() || null,
      idealAnswer: form.idealAnswer.trim() || null,
      tags: form.tags.trim() || null,
      isActive: form.isActive,
    };

    const editingId = this.editingQuestionId();
    const request$ = editingId
      ? this.api.updateQuestion(editingId, payload)
      : this.api.createQuestion(payload);

    request$.pipe(catchError(() => of(null))).subscribe((savedQuestion) => {
      this.questionSaving.set(false);
      if (!savedQuestion) {
        this.error.set('Unable to save question right now.');
        return;
      }

      if (editingId) {
        this.questions.update((items) =>
          items.map((item) => (item.id === savedQuestion.id ? savedQuestion : item))
        );
      } else {
        this.questions.update((items) => [savedQuestion, ...items]);
      }

      this.editingQuestionId.set(savedQuestion.id);
    });
  }

  deleteQuestion(questionId: number): void {
    this.api
      .deleteQuestion(questionId)
      .pipe(catchError(() => of(null)))
      .subscribe((result) => {
        if (result === null) {
          this.error.set('Unable to archive question.');
          return;
        }

        this.questions.update((items) => items.filter((item) => item.id !== questionId));
      });
  }

  openTagDialog(questionId: number): void {
    this.tagDialogQuestionId.set(questionId);
    this.tagDraft.set('');
    this.tagError.set(null);
  }

  closeTagDialog(): void {
    this.tagDialogQuestionId.set(null);
    this.tagDraft.set('');
    this.tagError.set(null);
    this.tagSaving.set(false);
  }

  setTagDraft(value: string): void {
    this.tagDraft.set(value.slice(0, 32));
    this.tagError.set(null);
  }

  submitTag(): void {
    const questionId = this.tagDialogQuestionId();
    if (!questionId || this.tagSaving()) {
      return;
    }

    const tag = this.tagDraft().trim();
    if (!tag) {
      this.tagError.set('Tag label is required.');
      return;
    }

    if (!/^[a-zA-Z0-9][a-zA-Z0-9 _-]{1,31}$/.test(tag)) {
      this.tagError.set('Tag must be 2-32 chars and use letters, numbers, spaces, _ or -.');
      return;
    }

    this.tagSaving.set(true);

    this.api
      .addQuestionTag(questionId, tag)
      .pipe(catchError(() => of(null)))
      .subscribe((updatedQuestion) => {
        this.tagSaving.set(false);
        if (!updatedQuestion) {
          this.tagError.set('Unable to add tag right now.');
          return;
        }

        this.questions.update((items) =>
          items.map((item) => (item.id === updatedQuestion.id ? updatedQuestion : item))
        );
        this.closeTagDialog();
      });
  }

  setMonitorUserInput(value: string | number | null): void {
    const normalized = String(value ?? '');
    this.monitorUserInput.set(normalized.replace(/[^\d]/g, '').slice(0, 10));
    this.monitorUserError.set(null);
  }

  loadMonitoredSessions(): void {
    const userIdError = this.validateUserId(this.monitorUserInput());
    if (userIdError) {
      this.monitorUserError.set(userIdError);
      return;
    }

    const userId = Number(this.monitorUserInput());

    this.sessionsLoading.set(true);
    this.api
      .getSessionsByUser(userId)
      .pipe(catchError(() => of([])))
      .subscribe((sessions) => {
        this.sessions.set(sessions);
        this.sessionsLoading.set(false);
      });
  }

  setReportsUserInput(value: string | number | null): void {
    const normalized = String(value ?? '');
    this.reportsUserInput.set(normalized.replace(/[^\d]/g, '').slice(0, 10));
    this.reportsUserError.set(null);
  }

  loadReports(): void {
    const userIdError = this.validateUserId(this.reportsUserInput());
    if (userIdError) {
      this.reportsUserError.set(userIdError);
      return;
    }

    const userId = Number(this.reportsUserInput());

    this.reportsLoading.set(true);
    this.api
      .getReportsByUser(userId)
      .pipe(catchError(() => of([])))
      .subscribe((reports) => {
        this.reports.set(reports);
        this.reportsLoading.set(false);
      });
  }

  openCandidateReport(reportId: number): void {
    this.router.navigate(['/dashboard/interview/report', reportId]);
  }

  openReportPdf(reportId: number): void {
    this.api
      .getReportPdfUrl(reportId)
      .pipe(catchError(() => of('')))
      .subscribe((url) => {
        if (!url) {
          return;
        }

        const normalized = this.api.resolveBackendAssetUrl(url);
        window.open(normalized, '_blank', 'noopener');
      });
  }

  openSession(sessionId: number): void {
    const session = this.sessions().find((item) => item.id === sessionId) ?? null;
    if (session?.mode === 'LIVE') {
      const subMode = session.liveSubMode ?? 'TEST_LIVE';
      this.router
        .navigate(['/dashboard/interview/live', sessionId], {
          queryParams: { subMode },
        })
        .catch(() => {
          this.router.navigate(['/interview/live', sessionId], {
            queryParams: { subMode },
          });
        });
      return;
    }

    this.router.navigate(['/dashboard/interview/session', sessionId]);
  }

  getStatusClass(status: SessionStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'status-completed';
      case 'IN_PROGRESS':
        return 'status-progress';
      case 'PAUSED':
        return 'status-paused';
      case 'ABANDONED':
        return 'status-abandoned';
      default:
        return 'status-evaluating';
    }
  }

  prettyDate(value: string | null): string {
    if (!value) {
      return '—';
    }
    return new Date(value).toLocaleString();
  }

  formatPercentile(value: number | null): string {
    if (value === null || value === undefined) {
      return '—';
    }

    const normalized = value <= 1 ? value * 100 : value;
    return `${normalized.toFixed(0)}%`;
  }

  updateQuestionFormField(field: keyof QuestionFormModel, value: string | number | boolean): void {
    this.questionForm.update((form) => ({
      ...form,
      [field]: value,
    }));
    this.questionFormError.set(null);
  }

  private defaultQuestionForm(): QuestionFormModel {
    return {
      careerPathId: 0,
      roleType: 'SE',
      questionText: '',
      type: 'TECHNICAL',
      difficulty: 'BEGINNER',
      category: '',
      domain: '',
      expectedPoints: '',
      hints: '',
      followUps: '',
      idealAnswer: '',
      tags: '',
      isActive: true,
    };
  }

  private dateValue(value: string | null): number {
    if (!value) {
      return 0;
    }
    return new Date(value).getTime();
  }

  private validateUserId(value: string): string | null {
    const raw = value.trim();
    if (!raw) {
      return 'User id is required.';
    }

    if (!/^\d+$/.test(raw)) {
      return 'User id must contain digits only.';
    }

    const parsed = Number(raw);
    if (!Number.isInteger(parsed) || parsed <= 0 || parsed > 2147483647) {
      return 'User id must be a positive integer.';
    }

    return null;
  }

  private validateQuestionForm(form: QuestionFormModel): string | null {
    const textLength = form.questionText.trim().length;
    if (textLength < 12) {
      return 'Question text must be at least 12 characters.';
    }

    if (textLength > 3000) {
      return 'Question text is too long.';
    }

    if (!Number.isInteger(form.careerPathId) || form.careerPathId <= 0) {
      return 'Career path id must be a positive integer.';
    }

    if (form.category.trim().length > 80) {
      return 'Category must be 80 characters or less.';
    }

    if (form.domain.trim().length > 80) {
      return 'Domain must be 80 characters or less.';
    }

    if (form.tags.trim().length > 320) {
      return 'Tags field is too long.';
    }

    return null;
  }
}
