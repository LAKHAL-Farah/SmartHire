import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of, switchMap } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';
import { InterviewApiService } from '../interview-api.service';
import {
  AnswerEvaluationDto,
  InterviewReportDto,
  InterviewSessionDto,
  SessionAnswerDto,
} from '../interview.models';
import { isCurrentInterviewUser, resolveCurrentUserId } from '../interview-user.util';

/* ── Types ── */
interface DimensionScore {
  label: string;
  score: number;
  outOf: number;
  color: string;
}

interface QuestionReview {
  questionId: number;
  answerId: number | null;
  number: number;
  text: string;
  answer: string;
  role: string;
  difficulty: string;
  category: string;
  dimensions: { label: string; score: number; color: string }[];
  feedback: string;
  followUp: string | null;
  submittedAt: string | null;
}

interface RecommendedAction {
  icon: string;
  title: string;
  description: string;
}

@Component({
  selector: 'app-interview-report',
  standalone: true,
  imports: [CommonModule, LUCIDE_ICONS],
  templateUrl: './interview-report.component.html',
  styleUrl: './interview-report.component.scss'
})
export class InterviewReportComponent implements OnInit, OnDestroy {
  private readonly api = inject(InterviewApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private pollRef: ReturnType<typeof setInterval> | null = null;
  private percentileAnimRef: ReturnType<typeof setInterval> | null = null;
  private toastTimeoutRef: ReturnType<typeof setTimeout> | null = null;

  readonly userId = resolveCurrentUserId();

  private readonly longDateFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });

  expandedQuestion = signal<number | null>(null);
  readonly isLoading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly processingHint = signal<string | null>(null);
  readonly report = signal<InterviewReportDto | null>(null);
  readonly session = signal<InterviewSessionDto | null>(null);
  readonly sessionAnswers = signal<SessionAnswerDto[]>([]);
  readonly evaluations = signal<AnswerEvaluationDto[]>([]);
  readonly animatedPercentile = signal(0);
  readonly actionToast = signal<string | null>(null);
  readonly downloadingPdf = signal(false);
  readonly bookmarkingByQuestion = signal<Record<number, 'idle' | 'saving' | 'saved' | 'error'>>({});

  readonly dimensions = computed<DimensionScore[]>(() => {
    const report = this.report();
    const fallbackFromAnswers = {
      content: this.avgEvalScore('contentScore'),
      clarity: this.avgEvalScore('clarityScore'),
      technical: this.avgEvalScore('technicalScore'),
      confidence: this.avgEvalScore('confidenceScore'),
      presence: this.avgEvalScore('postureScore'),
    };

    return [
      this.createDimension('Content', report?.contentAvg ?? fallbackFromAnswers.content, '#2ee8a5'),
      this.createDimension('Clarity', fallbackFromAnswers.clarity, '#3b82f6'),
      this.createDimension('Technical', report?.technicalAvg ?? fallbackFromAnswers.technical, '#8b5cf6'),
      this.createDimension('Confidence', fallbackFromAnswers.confidence, '#10b981'),
      this.createDimension('Presence', report?.presenceAvg ?? fallbackFromAnswers.presence, '#f59e0b'),
    ]
      .filter((dim): dim is DimensionScore => dim !== null)
      .map((dim) => ({ ...dim, score: this.clampScore(dim.score) }));
  });

  readonly radarPoints = computed(() => this.computeRadar(this.dimensions()));

  readonly strongestAreas = computed(() => {
    const fromReport = this.parseTextList(this.report()?.strengths);
    if (fromReport.length) {
      return fromReport;
    }

    return [...this.dimensions()]
      .sort((a, b) => b.score - a.score)
      .slice(0, 3)
      .map((dim) => `Strong ${dim.label.toLowerCase()} performance (${dim.score.toFixed(1)}/10).`);
  });

  readonly improvementAreas = computed(() => {
    const fromReport = this.parseTextList(this.report()?.weaknesses);
    if (fromReport.length) {
      return fromReport;
    }

    return [...this.dimensions()]
      .sort((a, b) => a.score - b.score)
      .slice(0, 3)
      .map((dim) => `Improve ${dim.label.toLowerCase()} with focused practice.`);
  });

  readonly recommendations = computed<RecommendedAction[]>(() => {
    const fromReport = this.parseTextList(this.report()?.recommendations);
    if (!fromReport.length) {
      return [];
    }

    const icons = ['🎯', '📚', '🛠️', '🧠', '✅'];
    return fromReport.slice(0, 5).map((item, index) => ({
      icon: icons[index % icons.length],
      title: item,
      description: 'Generated from your report recommendations.',
    }));
  });

  readonly questions = computed<QuestionReview[]>(() => this.buildQuestionReviews());
  readonly recruiterVerdict = computed(() => {
    const direct = this.report()?.recruiterVerdict?.trim();
    if (direct) {
      return direct;
    }

    const score = this.finalScore();
    if (score === null) {
      return 'Verdict is being generated by the evaluator. Please refresh shortly.';
    }
    if (score >= 8) {
      return 'Strong hire signal. Candidate demonstrates high readiness for this role.';
    }
    if (score >= 6.5) {
      return 'Promising profile. Recommend one focused preparation sprint and re-evaluation.';
    }
    return 'Not interview-ready yet. Target weaknesses and retake after structured practice.';
  });

  readonly sessionDate = computed(() => {
    const report = this.report();
    const session = this.session();
    return this.formatDate(report?.generatedAt ?? session?.startedAt ?? null);
  });

  readonly sessionType = computed(() => this.session()?.mode ?? '—');
  readonly questionType = computed(() => this.session()?.type ?? '—');
  readonly careerPath = computed(() => this.roleLabel(this.session()?.roleType ?? null));
  readonly duration = computed(() => this.formatDuration(this.resolveDurationSeconds(this.session())));
  readonly finalScore = computed(() => {
    const score = this.report()?.finalScore ?? this.session()?.totalScore ?? null;
    return score === null ? null : this.clampScore(score);
  });
  readonly percentile = computed(() => {
    const raw = this.report()?.percentileRank;
    if (raw === null || raw === undefined) {
      return null;
    }

    const normalized = raw <= 1 ? raw * 100 : raw;
    return Math.max(0, Math.min(100, normalized));
  });

  ngOnDestroy(): void {
    this.clearTimers();
  }

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    const reportId = Number(rawId);

    if (!Number.isFinite(reportId)) {
      this.loadError.set('Invalid report id.');
      this.isLoading.set(false);
      return;
    }

    this.loadReport(reportId);
  }

  toggleQuestion(n: number): void {
    this.expandedQuestion.update((value) => (value === n ? null : n));
  }

  retakeSession(): void {
    const mode = this.session()?.mode;
    const role = this.session()?.roleType;
    const type = this.session()?.type;
    this.router.navigate(['/dashboard/interview/setup'], {
      queryParams: {
        ...(mode ? { mode } : {}),
        ...(role ? { role } : {}),
        ...(type ? { type } : {}),
      },
    });
  }

  downloadPdf(): void {
    const reportId = this.report()?.id;
    if (!reportId || this.downloadingPdf()) {
      return;
    }

    this.downloadingPdf.set(true);
    this.generateVisualPdf(reportId)
      .then(() => {
        this.downloadingPdf.set(false);
        this.showToast('Visual PDF exported successfully.');
      })
      .catch(() => {
        this.api
          .getReportPdfUrl(reportId)
          .pipe(catchError(() => of('')))
          .subscribe((pdfPath) => {
            this.downloadingPdf.set(false);
            if (!pdfPath) {
              this.showToast('Unable to generate PDF right now.');
              return;
            }

            const normalized = this.api.resolveBackendAssetUrl(pdfPath);
            window.open(normalized, '_blank', 'noopener');
            this.showToast('Opened server-generated PDF.');
          });
      });
  }

  async shareReport(): Promise<void> {
    const url = window.location.href;
    try {
      await navigator.clipboard.writeText(url);
      this.showToast('Report link copied to clipboard.');
    } catch {
      this.showToast('Unable to copy link automatically.');
    }
  }

  bookmarkQuestion(question: QuestionReview): void {
    const userId = this.userId;
    if (!question.questionId || !userId) {
      return;
    }

    this.updateBookmarkState(question.questionId, 'saving');
    this.api
      .addBookmark({
        userId,
        questionId: question.questionId,
        note: `Saved from report #${this.report()?.id ?? ''}`.trim(),
        tagLabel: question.category,
      })
      .pipe(catchError(() => of(null)))
      .subscribe((bookmark) => {
        if (!bookmark) {
          this.updateBookmarkState(question.questionId, 'error');
          return;
        }

        this.updateBookmarkState(question.questionId, 'saved');
      });
  }

  bookmarkLabel(questionId: number): string {
    if (!this.userId) {
      return 'Sign in required';
    }

    const state = this.bookmarkingByQuestion()[questionId] ?? 'idle';
    if (state === 'saving') {
      return 'Saving...';
    }
    if (state === 'saved') {
      return 'Saved';
    }
    if (state === 'error') {
      return 'Retry Save';
    }
    return 'Bookmark';
  }

  bookmarkDisabled(questionId: number): boolean {
    return !this.userId || (this.bookmarkingByQuestion()[questionId] ?? 'idle') === 'saving';
  }

  backToHub(): void {
    this.router.navigate(['/dashboard/interview']);
  }

  private loadReport(reportId: number): void {
    this.isLoading.set(true);
    this.loadError.set(null);

    this.api
      .getReportById(reportId)
      .pipe(
        switchMap((report) => {
          if (!isCurrentInterviewUser(report.userId)) {
            this.loadError.set('This report does not belong to user #1.');
            return of(null);
          }

          return forkJoin({
            report: of(report),
            session: this.api.getSessionById(report.sessionId).pipe(catchError(() => of(null))),
            answers: this.api.getAnswersBySession(report.sessionId).pipe(catchError(() => of([]))),
            evaluations: this.api.getEvaluationsBySession(report.sessionId).pipe(catchError(() => of([]))),
          });
        }),
        catchError(() => {
          this.loadError.set('Unable to load report data.');
          return of(null);
        })
      )
      .subscribe((result) => {
        if (!result) {
          this.isLoading.set(false);
          return;
        }

        if (result.session && !isCurrentInterviewUser(result.session.userId)) {
          this.loadError.set('This report session does not belong to user #1.');
          this.isLoading.set(false);
          return;
        }

        this.report.set(result.report);
        this.session.set(result.session);
        this.sessionAnswers.set(result.answers);
        this.evaluations.set(result.evaluations);
        this.animatePercentileTo(this.percentile() ?? 0);

        if (result.report.finalScore === null) {
          this.processingHint.set('Your final score is still processing. We are refreshing automatically...');
          this.startReportPolling(reportId);
        } else {
          this.processingHint.set(null);
        }

        this.isLoading.set(false);
      });
  }

  private buildQuestionReviews(): QuestionReview[] {
    const session = this.session();
    if (!session) {
      return [];
    }

    const orders = [...(session.questionOrders ?? [])].sort((a, b) => a.questionOrder - b.questionOrder);
    const answers = this.allAnswers();
    const evaluationByAnswerId = this.evaluationByAnswerId();
    const answerByQuestion = new Map<number, SessionAnswerDto>();

    for (const answer of answers) {
      if (answer.isFollowUp) {
        continue;
      }

      if (!answerByQuestion.has(answer.questionId)) {
        answerByQuestion.set(answer.questionId, answer);
      }
    }

    if (!orders.length) {
      return answers.map((answer, index) => {
        const evaluation = answer.answerEvaluation ?? evaluationByAnswerId.get(answer.id) ?? null;
        return {
          questionId: answer.questionId,
          answerId: answer.id,
          number: index + 1,
          text: `Question ${index + 1}`,
          answer: answer.answerText?.trim() || answer.codeAnswer?.trim() || 'No answer submitted.',
          role: this.roleLabel(this.session()?.roleType ?? null),
          difficulty: '—',
          category: 'GENERAL',
          dimensions: this.extractQuestionDimensions(evaluation),
          feedback: evaluation?.aiFeedback?.trim() || 'No AI feedback generated for this answer.',
          followUp: evaluation?.followUpGenerated ?? null,
          submittedAt: answer.submittedAt,
        };
      });
    }

    return orders.map((order, index) => {
      const answer = answerByQuestion.get(order.questionId);
      const evaluation = answer?.answerEvaluation ?? (answer ? evaluationByAnswerId.get(answer.id) : null) ?? null;
      return {
        questionId: order.questionId,
        answerId: answer?.id ?? null,
        number: index + 1,
        text: order.question?.questionText ?? `Question ${index + 1}`,
        answer: answer?.answerText?.trim() || answer?.codeAnswer?.trim() || 'No answer submitted.',
        role: this.roleLabel(order.question?.roleType ?? this.session()?.roleType ?? null),
        difficulty: order.question?.difficulty ?? '—',
        category: order.question?.type ?? 'GENERAL',
        dimensions: this.extractQuestionDimensions(evaluation),
        feedback: evaluation?.aiFeedback?.trim() || 'No AI feedback generated for this answer.',
        followUp: evaluation?.followUpGenerated ?? null,
        submittedAt: answer?.submittedAt ?? null,
      };
    });
  }

  private allAnswers(): SessionAnswerDto[] {
    const fromSession = this.session()?.answers ?? [];
    const fromEndpoint = this.sessionAnswers();
    const byId = new Map<number, SessionAnswerDto>();

    for (const answer of [...fromSession, ...fromEndpoint]) {
      if (!answer || answer.isFollowUp) {
        continue;
      }

      byId.set(answer.id, answer);
    }

    return [...byId.values()].sort((a, b) => this.dateValue(a.submittedAt) - this.dateValue(b.submittedAt));
  }

  private evaluationByAnswerId(): Map<number, AnswerEvaluationDto> {
    const map = new Map<number, AnswerEvaluationDto>();

    for (const evaluation of this.evaluations()) {
      map.set(evaluation.answerId, evaluation);
    }

    return map;
  }

  private extractQuestionDimensions(evaluation: AnswerEvaluationDto | null): Array<{ label: string; score: number; color: string }> {
    if (!evaluation) {
      return [];
    }

    const dims: Array<{ label: string; score: number; color: string } | null> = [
      this.questionDimension('Content', evaluation.contentScore, '#2ee8a5'),
      this.questionDimension('Clarity', evaluation.clarityScore, '#3b82f6'),
      this.questionDimension('Technical', evaluation.technicalScore, '#8b5cf6'),
      this.questionDimension('Confidence', evaluation.confidenceScore, '#10b981'),
      this.questionDimension('Tone', evaluation.toneScore, '#f59e0b'),
    ];

    return dims.filter((dim): dim is { label: string; score: number; color: string } => dim !== null);
  }

  private questionDimension(label: string, score: number | null, color: string): { label: string; score: number; color: string } | null {
    if (score === null || score === undefined) {
      return null;
    }

    const normalized = this.clampScore(score);
    return {
      label,
      score: normalized,
      color,
    };
  }

  private createDimension(label: string, score: number | null | undefined, color: string): DimensionScore | null {
    if (score === null || score === undefined) {
      return null;
    }

    return {
      label,
      score,
      outOf: 10,
      color,
    };
  }

  private avgEvalScore(key: keyof AnswerEvaluationDto): number | null {
    const evaluations = this.evaluations().length
      ? this.evaluations()
      : this.allAnswers().map((answer) => answer.answerEvaluation).filter((value): value is AnswerEvaluationDto => !!value);
    const scores = evaluations
      .map((evaluation) => evaluation?.[key])
      .filter((score): score is number => typeof score === 'number');

    if (!scores.length) {
      return null;
    }

    const total = scores.reduce((sum, value) => sum + value, 0);
    return total / scores.length;
  }

  private parseTextList(raw: string | null | undefined): string[] {
    if (!raw) {
      return [];
    }

    const normalized = raw.trim();
    if (!normalized) {
      return [];
    }

    try {
      const parsed = JSON.parse(normalized);
      if (Array.isArray(parsed)) {
        return parsed.map((value) => String(value).trim()).filter(Boolean);
      }
    } catch {
      // Fall back to plain-text parsing.
    }

    return normalized
      .split(/\r?\n|;/)
      .map((line) => line.replace(/^[-*]\s*/, '').trim())
      .filter(Boolean);
  }

  private formatDate(value: string | null): string {
    if (!value) {
      return '—';
    }

    return this.longDateFormatter.format(new Date(value));
  }

  formatDateTime(value: string | null): string {
    if (!value) {
      return '—';
    }

    const date = new Date(value);
    return `${this.longDateFormatter.format(date)} ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  }

  private formatDuration(seconds: number | null): string {
    if (seconds === null || seconds === undefined || seconds < 0) {
      return '—';
    }

    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins} min ${secs.toString().padStart(2, '0')} sec`;
  }

  private resolveDurationSeconds(session: InterviewSessionDto | null): number | null {
    if (!session) {
      return null;
    }

    if (session.durationSeconds && session.durationSeconds > 0) {
      return session.durationSeconds;
    }

    if (session.startedAt && session.endedAt) {
      const start = new Date(session.startedAt).getTime();
      const end = new Date(session.endedAt).getTime();
      const deltaSeconds = Math.floor((end - start) / 1000);
      return deltaSeconds > 0 ? deltaSeconds : null;
    }

    return null;
  }

  private roleLabel(roleType: InterviewSessionDto['roleType'] | null): string {
    switch (roleType) {
      case 'SE':
        return 'Software Engineer';
      case 'CLOUD':
        return 'Cloud Engineer';
      case 'AI':
        return 'AI Engineer';
      case 'ALL':
        return 'General';
      default:
        return '—';
    }
  }

  private clampScore(value: number): number {
    return Math.max(0, Math.min(10, value));
  }

  private dateValue(value: string | null): number {
    if (!value) {
      return 0;
    }

    return new Date(value).getTime();
  }

  private startReportPolling(reportId: number): void {
    let attempts = 0;
    this.stopPoll();
    this.pollRef = setInterval(() => {
      attempts += 1;
      this.api
        .getReportById(reportId)
        .pipe(catchError(() => of(null)))
        .subscribe((freshReport) => {
          if (!freshReport) {
            return;
          }

          this.report.set(freshReport);
          this.animatePercentileTo(this.percentile() ?? 0);

          if (freshReport.finalScore !== null) {
            this.processingHint.set(null);
            this.stopPoll();
          }

          if (attempts >= 20) {
            this.processingHint.set('Report processing is taking longer than expected. Please refresh soon.');
            this.stopPoll();
          }
        });
    }, 3000);
  }

  private animatePercentileTo(target: number): void {
    if (this.percentileAnimRef) {
      clearInterval(this.percentileAnimRef);
      this.percentileAnimRef = null;
    }

    const start = this.animatedPercentile();
    if (start === target) {
      return;
    }

    const delta = target - start;
    const step = delta / 24;
    let current = start;
    let ticks = 0;

    this.percentileAnimRef = setInterval(() => {
      ticks += 1;
      current += step;
      if (ticks >= 24) {
        this.animatedPercentile.set(target);
        clearInterval(this.percentileAnimRef!);
        this.percentileAnimRef = null;
        return;
      }

      this.animatedPercentile.set(Math.max(0, Math.min(100, current)));
    }, 25);
  }

  private showToast(message: string): void {
    this.actionToast.set(message);
    if (this.toastTimeoutRef) {
      clearTimeout(this.toastTimeoutRef);
    }

    this.toastTimeoutRef = setTimeout(() => {
      this.actionToast.set(null);
      this.toastTimeoutRef = null;
    }, 2200);
  }

  private updateBookmarkState(questionId: number, state: 'idle' | 'saving' | 'saved' | 'error'): void {
    this.bookmarkingByQuestion.update((existing) => ({
      ...existing,
      [questionId]: state,
    }));
  }

  private stopPoll(): void {
    if (this.pollRef) {
      clearInterval(this.pollRef);
      this.pollRef = null;
    }
  }

  private clearTimers(): void {
    this.stopPoll();
    if (this.percentileAnimRef) {
      clearInterval(this.percentileAnimRef);
      this.percentileAnimRef = null;
    }
    if (this.toastTimeoutRef) {
      clearTimeout(this.toastTimeoutRef);
      this.toastTimeoutRef = null;
    }
  }

  private async generateVisualPdf(reportId: number): Promise<void> {
    const { default: jsPDF } = await import('jspdf');
    const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });

    const pageWidth = pdf.internal.pageSize.getWidth();
    const pageHeight = pdf.internal.pageSize.getHeight();
    const margin = 15;
    const lineHeight = 5.5;
    let yPos = margin;

    const renderWrappedText = (text: string, x: number, yPosCurrent: number, maxWidth: number, fontSize: number = 9): number => {
      pdf.setFont('Helvetica', 'normal');
      pdf.setFontSize(fontSize);
      pdf.setTextColor(50, 50, 50);
      
      const wrapped = pdf.splitTextToSize(text, maxWidth);
      let currentY = yPosCurrent;
      
      wrapped.forEach((line: string) => {
        pdf.text(line, x, currentY);
        currentY += lineHeight;
      });
      
      return currentY + 1;
    };

    const ensureSpace = (requiredHeight: number): void => {
      if (yPos + requiredHeight > pageHeight - 15) {
        pdf.addPage();
        yPos = margin;
      }
    };

    // ── Title & Metadata ──
    pdf.setFillColor(46, 232, 165);
    pdf.rect(0, 0, pageWidth, 25, 'F');
    
    pdf.setFont('Helvetica', 'bold');
    pdf.setTextColor(255, 255, 255);
    pdf.setFontSize(20);
    pdf.text('SmartHire Interview Report', margin, 12);
    pdf.setFontSize(9);
    pdf.text(`Report ID: ${reportId}`, margin, 18);

    yPos = 32;

    // ── Session Metadata ──
    pdf.setTextColor(100, 100, 100);
    pdf.setFontSize(9);
    const sessionDate = this.sessionDate() || 'N/A';
    const sessionInfo = `${sessionDate} · ${this.sessionType()} · ${this.questionType()}`;
    pdf.text(sessionInfo, margin, yPos);
    yPos += 8;

    // ── Score Section ──
    const finalScore = this.finalScore();
    pdf.setDrawColor(46, 232, 165);
    pdf.setLineWidth(0.5);
    pdf.rect(margin, yPos, pageWidth - 2 * margin, 35);

    pdf.setTextColor(0, 0, 0);
    pdf.setFont('Helvetica', 'bold');
    pdf.setFontSize(14);
    pdf.text('Overall Score', margin + 5, yPos + 10);

    pdf.setFontSize(32);
    const scoreText = finalScore === null ? '—' : finalScore.toFixed(1);
    pdf.text(scoreText, margin + 60, yPos + 28);

    pdf.setFontSize(10);
    pdf.setFont('Helvetica', 'normal');
    pdf.text('/10', margin + 95, yPos + 28);

    // Percentile info
    const percentile = this.percentile();
    pdf.setFontSize(9);
    pdf.setTextColor(100, 100, 100);
    if (percentile !== null) {
      pdf.text(`Percentile: ${percentile.toFixed(0)}%`, margin + 130, yPos + 15);
    }
    pdf.text(this.careerPath() || 'N/A', margin + 130, yPos + 22);
    pdf.text(this.duration() || 'N/A', margin + 130, yPos + 29);

    yPos += 42;

    // ── Verdict ──
    ensureSpace(20);
    const verdict = this.recruiterVerdict() || 'Verdict pending.';
    pdf.setTextColor(0, 0, 0);
    pdf.setFont('Helvetica', 'bold');
    pdf.setFontSize(11);
    pdf.text('Verdict', margin, yPos);
    yPos += 6;

    yPos = renderWrappedText(verdict, margin, yPos, pageWidth - 2 * margin - 5);
    yPos += 3;

    // ── Performance Dimensions ──
    ensureSpace(40);
    pdf.setTextColor(0, 0, 0);
    pdf.setFont('Helvetica', 'bold');
    pdf.setFontSize(12);
    pdf.text('Performance Dimensions', margin, yPos);
    yPos += 8;

    const dimensions = this.dimensions();
    dimensions.forEach((dim) => {
      pdf.setFontSize(9);
      pdf.setFont('Helvetica', 'normal');
      pdf.setTextColor(80, 80, 80);
      pdf.text(dim.label, margin, yPos);
      pdf.text(`${dim.score.toFixed(1)}/10`, pageWidth - margin - 20, yPos);

      // Progress bar
      const barWidth = 60;
      const barX = margin + 50;
      pdf.setDrawColor(220, 220, 220);
      pdf.rect(barX, yPos - 2.5, barWidth, 3);

      const displayColor = dim.color.replace(/#/g, '').match(/.{1,2}/g);
      if (displayColor) {
        const [r, g, b] = displayColor.map((x) => parseInt(x, 16));
        pdf.setFillColor(r, g, b);
      }
      const fillWidth = (dim.score / 10) * barWidth;
      pdf.rect(barX, yPos - 2.5, fillWidth, 3, 'F');

      yPos += lineHeight + 1;
    });

    yPos += 3;

    // ── Strengths ──
    ensureSpace(35);
    pdf.setFont('Helvetica', 'bold');
    pdf.setFontSize(11);
    pdf.setTextColor(0, 0, 0);
    pdf.text('Strengths', margin, yPos);
    yPos += 6;

    const strengths = this.strongestAreas();
    strengths.slice(0, 3).forEach((strength) => {
      yPos = renderWrappedText(`• ${strength}`, margin + 3, yPos, pageWidth - 2 * margin - 8, 8.5);
      yPos += 1;
    });

    // ── Improvements ──
    ensureSpace(35);
    pdf.setFont('Helvetica', 'bold');
    pdf.setFontSize(11);
    pdf.setTextColor(0, 0, 0);
    pdf.text('Areas for Improvement', margin, yPos);
    yPos += 6;

    const improvements = this.improvementAreas();
    improvements.slice(0, 3).forEach((improvement) => {
      yPos = renderWrappedText(`• ${improvement}`, margin + 3, yPos, pageWidth - 2 * margin - 8, 8.5);
      yPos += 1;
    });

    // ── Footer ──
    pdf.setFont('Helvetica', 'normal');
    pdf.setFontSize(8);
    pdf.setTextColor(150, 150, 150);
    const generatedOn = new Date().toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
    pdf.text(`Generated on ${generatedOn}`, margin, pageHeight - 10);
    pdf.text(`SmartHire © 2026`, pageWidth - margin - 30, pageHeight - 10);

    const fileName = `smarthire-report-${reportId}-${new Date().toISOString().slice(0, 10)}.pdf`;
    pdf.save(fileName);
  }

  private computeRadar(dimensions: DimensionScore[]): string {
    if (!dimensions.length) {
      return '';
    }

    const cx = 100;
    const cy = 100;
    const r = 70;
    const scores = dimensions.map((dim) => dim.score / dim.outOf);
    const angleStep = (2 * Math.PI) / scores.length;
    const points = scores.map((s, i) => {
      const angle = angleStep * i - Math.PI / 2;
      const x = cx + r * s * Math.cos(angle);
      const y = cy + r * s * Math.sin(angle);
      return `${x},${y}`;
    });
    return points.join(' ');
  }

  getRadarAxisPoints(): { label: string; x: number; y: number; lx: number; ly: number }[] {
    const cx = 100, cy = 100, r = 70;
    const dims = this.dimensions();
    const n = dims.length;
    if (!n) {
      return [];
    }

    const angleStep = (2 * Math.PI) / n;
    return dims.map((d, i) => {
      const angle = angleStep * i - Math.PI / 2;
      return {
        label: d.label,
        x: cx + r * Math.cos(angle),
        y: cy + r * Math.sin(angle),
        lx: cx + (r + 18) * Math.cos(angle),
        ly: cy + (r + 18) * Math.sin(angle),
      };
    });
  }

  getGridPolygon(scale: number): string {
    const cx = 100, cy = 100, r = 70;
    const n = this.dimensions().length;
    if (!n) {
      return '';
    }

    const angleStep = (2 * Math.PI) / n;
    const points: string[] = [];
    for (let i = 0; i < n; i++) {
      const angle = angleStep * i - Math.PI / 2;
      points.push(`${cx + r * scale * Math.cos(angle)},${cy + r * scale * Math.sin(angle)}`);
    }
    return points.join(' ');
  }

  getDimensionDots(q: QuestionReview): string {
    return q.dimensions.map((d) => d.color).join(',');
  }
}