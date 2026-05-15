import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom, Subscription } from 'rxjs';
import { LUCIDE_ICONS } from '../../../../../shared/lucide-icons';
import { TtsService } from '../../../../../shared/services/tts.service';
import { SessionEvent, WebSocketService } from '../../../../../shared/services/websocket.service';
import { InterviewApiService } from '../interview-api.service';
import { isCurrentInterviewUser, resolveCurrentUserId } from '../interview-user.util';
import { BookmarkButtonComponent } from '../components/bookmark-button/bookmark-button.component';
import { CloudInterviewComponent } from '../components/cloud-interview/cloud-interview.component';
import { CodingInterviewComponent } from '../components/coding-interview/coding-interview.component';
import { VerbalInterviewComponent } from '../components/verbal-interview/verbal-interview.component';
import { ComingSoonComponent } from '../components/coming-soon/coming-soon.component';
import { MlPipelineComponent } from '../../../../../interview/ml-pipeline/ml-pipeline.component';
import { AnswerService } from '../services/answer.service';
import { StreakService } from '../services/streak.service';
import {
  AnswerEvaluationDto,
  InterviewQuestionDto,
  InterviewReportDto,
  InterviewSessionDto,
  SessionAnswerDto,
  SessionQuestionOrderDto,
} from '../interview.models';

const EVALUATION_WAIT_TIMEOUT_MS = 70_000;
const EVALUATION_POLL_INTERVAL_MS = 2000;
const EVALUATION_POLL_ATTEMPTS = 35;
const REPORT_READY_POLL_INTERVAL_MS = 1500;
const REPORT_REDIRECT_DELAY_MS = 550;
const TIMER_RADIUS = 54;
const TIMER_CIRCUMFERENCE = 2 * Math.PI * TIMER_RADIUS;

@Component({
  selector: 'app-interview-session',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LUCIDE_ICONS,
    BookmarkButtonComponent,
    CloudInterviewComponent,
    CodingInterviewComponent,
    VerbalInterviewComponent,
    ComingSoonComponent,
    MlPipelineComponent,
  ],
  templateUrl: './interview-session.component.html',
  styleUrl: './interview-session.component.scss'
})
export class InterviewSessionComponent implements OnInit, OnDestroy {
  private readonly api = inject(InterviewApiService);
  private readonly answerService = inject(AnswerService);
  private readonly streakService = inject(StreakService);
  private readonly ttsService = inject(TtsService);
  private readonly websocketService = inject(WebSocketService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private sessionId = 0;
  private elapsedTimerRef: ReturnType<typeof setInterval> | null = null;
  private questionTimerRef: ReturnType<typeof setInterval> | null = null;
  private scoreAnimRef: ReturnType<typeof setInterval> | null = null;
  private wsConnectionSub: Subscription | null = null;
  private wsSessionSub: Subscription | null = null;
  private pendingEvaluations = new Map<
    number,
    {
      resolve: (evaluation: AnswerEvaluationDto) => void;
      reject: (reason?: unknown) => void;
      timeoutRef: ReturnType<typeof setTimeout>;
    }
  >();

  readonly session = signal<InterviewSessionDto | null>(null);
  readonly questionOrders = signal<SessionQuestionOrderDto[]>([]);
  readonly currentQuestion = signal<InterviewQuestionDto | null>(null);
  readonly currentIndex = signal(0);
  readonly answerText = signal('');
  readonly submissionMode = signal<'text' | 'audio'>('text');
  readonly transcribingMessage = signal('');
  readonly isWsConnected = signal(false);
  readonly activeView = signal<'verbal' | 'coding' | 'cloud-canvas' | 'ai-ml' | 'coming-soon-ai'>('verbal');
  readonly questionMetadata = signal<any>(null);
  readonly mlFollowUpText = signal<string | null>(null);
  readonly audioBlocked = signal(false);
  readonly audioTranscriptDebug = signal<string | null>(null);
  readonly audioEvaluationDebug = signal<AnswerEvaluationDto | null>(null);
  readonly isAnswerFocused = signal(false);
  readonly isSubmitting = signal(false);
  readonly isLoaded = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly hintOpen = signal(false);
  readonly feedbackOpen = signal(false);
  readonly feedbackEvaluation = signal<AnswerEvaluationDto | null>(null);
  readonly animatedScore = signal(0);
  readonly lastAnswerId = signal<number | null>(null);
  readonly idealExpanded = signal(false);
  readonly idealReveal = signal(false);

  readonly followUpInputOpen = signal(false);
  readonly showFollowUpDialog = signal(false);
  readonly followUpText = signal('');
  readonly followUpBusy = signal(false);
  readonly followUpState = signal<string | null>(null);

  readonly showAbandonConfirm = signal(false);
  readonly showStreakCelebration = signal(false);
  readonly reportPreparing = signal(false);
  readonly reportPreparingMessage = signal('Getting the report ready...');
  readonly streakCelebrationValue = signal(0);
  readonly celebrationConfettiPieces = Array.from({ length: 20 }, (_, index) => index);

  readonly elapsedSeconds = signal(0);
  readonly countdownTotal = signal(120);
  readonly countdownLeft = signal(120);
  readonly currentUserId = resolveCurrentUserId();

  readonly isPractice = computed(() => this.session()?.mode === 'PRACTICE');
  readonly isTest = computed(() => this.session()?.mode === 'TEST');
  readonly totalQuestions = computed(() => this.questionOrders().length);
  readonly questionPositionLabel = computed(() => {
    const total = this.totalQuestions();
    if (!total) {
      return 'Q0 of 0';
    }
    return `Q${Math.min(this.currentIndex() + 1, total)} of ${total}`;
  });
  readonly progressPercent = computed(() => {
    const total = this.totalQuestions();
    if (!total) {
      return 0;
    }

    return (Math.min(this.currentIndex() + 1, total) / total) * 100;
  });
  readonly characterCount = computed(() => this.answerText().length);
  readonly avatarStatus = computed(() => {
    if (this.isSubmitting()) {
      return 'Thinking...';
    }

    if (this.isAnswerFocused()) {
      return 'Listening...';
    }

    return 'Ready for your answer';
  });
  readonly phaseLabel = computed(() => this.currentQuestion()?.type ?? 'TECHNICAL');
  readonly hints = computed(() => this.parseJsonArray(this.currentQuestion()?.hints));
  readonly expectedPoints = computed(() => this.parseJsonArray(this.currentQuestion()?.expectedPoints));
  readonly timerRingOffset = computed(() => {
    if (!this.isTest()) {
      return TIMER_CIRCUMFERENCE;
    }

    const total = this.countdownTotal() || 1;
    const ratio = Math.max(0, Math.min(1, this.countdownLeft() / total));
    return TIMER_CIRCUMFERENCE * (1 - ratio);
  });
  readonly timerDisplay = computed(() => {
    const value = this.isTest() ? this.countdownLeft() : this.elapsedSeconds();
    return this.toClock(value);
  });
  readonly scoreTextColor = computed(() => {
    const score = this.feedbackEvaluation()?.overallScore ?? 0;
    if (score < 5) {
      return '#ef4444';
    }
    if (score <= 7) {
      return '#f59e0b';
    }
    return '#22c55e';
  });
  readonly contentScoreLabel = computed(() => {
    const score = this.feedbackEvaluation()?.contentScore;
    return score === null || score === undefined ? '--' : score.toFixed(1);
  });

  readonly timerRadius = TIMER_RADIUS;
  readonly timerCircumference = TIMER_CIRCUMFERENCE;

  @ViewChild('mlPipeline') mlPipelineComponent?: MlPipelineComponent;

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    const id = Number(rawId);
    if (!Number.isFinite(id)) {
      this.loadError.set('Invalid session id.');
      return;
    }

    this.sessionId = id;
    if (this.currentUserId) {
      this.streakService.ensureLoaded(this.currentUserId).subscribe();
    }

    this.initializeRealtime();
    this.bootstrapSession();
  }

  ngOnDestroy(): void {
    this.ttsService.stop();
    this.rejectPendingEvaluations('Interview room closed before evaluation completed.');
    this.wsSessionSub?.unsubscribe();
    this.wsConnectionSub?.unsubscribe();
    this.websocketService.unsubscribeFromSession(this.sessionId);
    this.clearIntervals();
  }

  playCurrentQuestionAudio(): void {
    this.playQuestionAudio(this.currentQuestion());
  }

  loadQuestion(question: InterviewQuestionDto | null): void {
    this.currentQuestion.set(question);
    this.questionMetadata.set(null);
    this.activeView.set('verbal');
    this.mlFollowUpText.set(null);
    this.audioBlocked.set(false);
    this.ttsService.stop();

    if (!question) {
      return;
    }

    if (question.metadata) {
      try {
        this.questionMetadata.set(JSON.parse(question.metadata));
      } catch {
        this.questionMetadata.set(null);
      }
    }

    const role = String(question.roleType ?? '').toUpperCase();
    const normalizedRole = role.replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
    const type = String(question.type ?? '').toUpperCase();
    const mode = String(this.questionMetadata()?.mode ?? '').toLowerCase();

    const isSoftwareEngineeringRole =
      normalizedRole === 'SE' ||
      normalizedRole === 'SOFTWARE_ENGINEER' ||
      normalizedRole === 'SOFTWARE_ENGINEERING' ||
      normalizedRole === 'SOFTWAREENGINEER';

    if (type === 'CODING' && isSoftwareEngineeringRole) {
      this.activeView.set('coding');
    } else if (normalizedRole === 'CLOUD' || normalizedRole === 'CLOUD_ENGINEER') {
      if (mode === 'canvas') {
        this.activeView.set('cloud-canvas');
      } else {
        this.activeView.set('verbal');
      }
    } else if (normalizedRole === 'AI' || normalizedRole === 'AI_ENGINEER') {
      if (type === 'TECHNICAL' || type === 'SITUATIONAL') {
        this.activeView.set(this.isMlPipelineQuestion(question) ? 'ai-ml' : 'verbal');
      } else {
        this.activeView.set('verbal');
      }
    } else {
      this.activeView.set('verbal');
    }

    if (question.ttsAudioUrl) {
      setTimeout(() => {
        this.ttsService.playFromUrl(question.ttsAudioUrl as string).catch(() => {
          this.audioBlocked.set(true);
        });
      }, 500);
    }
  }

  private isMlPipelineQuestion(question: InterviewQuestionDto): boolean {
    const text = String(question.questionText ?? '').toLowerCase();
    return text.includes('pipeline');
  }

  onAnswerSubmitted(answer: SessionAnswerDto | null): void {
    if (!answer?.id) {
      return;
    }

    if (this.activeView() === 'ai-ml') {
      this.lastAnswerId.set(answer.id);
      this.mlPipelineComponent?.onAnswerSubmitted(answer.id);
    }

    if (this.activeView() === 'cloud-canvas') {
      void this.advanceCloudCanvasOrComplete();
      return;
    }

    this.startEvaluationPolling(answer.id);
  }

  private async advanceCloudCanvasOrComplete(): Promise<void> {
    try {
      const nextQuestion = await firstValueFrom(this.api.getNextSessionQuestion(this.sessionId));
      if (!nextQuestion) {
        await this.completeAndGenerateReport();
        return;
      }

      const [session, questionOrders] = await Promise.all([
        firstValueFrom(this.api.getSessionById(this.sessionId)),
        firstValueFrom(this.api.getSessionQuestionOrder(this.sessionId)),
      ]);

      this.session.set(session);
      this.questionOrders.set(questionOrders);
      this.currentIndex.set(this.resolveCurrentIndex(session, questionOrders, nextQuestion));
      this.answerText.set('');
      this.configureQuestionTimer();
      this.loadQuestion(nextQuestion);
    } catch {
      this.loadError.set('Could not move to the next question.');
    }
  }

  async submitAnswer(isAutoSubmit = false): Promise<void> {
    if (this.isSubmitting()) {
      return;
    }

    const currentQuestion = this.currentQuestion();
    if (!currentQuestion) {
      return;
    }

    const typed = this.answerText().trim();
    if (!typed && !isAutoSubmit) {
      return;
    }

    this.isSubmitting.set(true);
    this.hintOpen.set(false);
    this.followUpInputOpen.set(false);
    this.showFollowUpDialog.set(false);
    this.followUpState.set(null);

    try {
      const submitResponse = await firstValueFrom(
        this.answerService.submitTextAnswer(this.sessionId, currentQuestion.id, typed || 'No answer provided.')
      );

      this.lastAnswerId.set(submitResponse.id);

      const evaluationPromise = this.waitForEvaluationEvent(submitResponse.id);
      const evaluation = await evaluationPromise;

      if (this.isPractice()) {
        this.feedbackEvaluation.set(evaluation);
        this.openFeedbackDrawer();
      } else {
        await this.advanceSessionOrComplete();
      }
    } catch {
      this.loadError.set('Unable to submit answer. Please try again.');
    } finally {
      this.transcribingMessage.set('');
      this.isSubmitting.set(false);
    }
  }

  async onAudioReady(audioBlob: Blob): Promise<void> {
    const currentQuestion = this.currentQuestion();
    if (!currentQuestion || this.isSubmitting()) {
      return;
    }

    this.isSubmitting.set(true);
    this.transcribingMessage.set('Transcribing your answer...');
    this.audioTranscriptDebug.set(null);
    this.audioEvaluationDebug.set(null);
    this.hintOpen.set(false);
    this.followUpInputOpen.set(false);
    this.showFollowUpDialog.set(false);
    this.followUpState.set(null);

    try {
      const submitResponse = await firstValueFrom(
        this.answerService.submitAudioAnswer(this.sessionId, currentQuestion.id, audioBlob)
      );

      this.lastAnswerId.set(submitResponse.id);
      this.audioTranscriptDebug.set(submitResponse.answerText ?? '[No transcript returned]');
      this.transcribingMessage.set('Transcript received. Evaluating...');

      const evaluationPromise = this.waitForEvaluationEvent(submitResponse.id);
      const evaluation = await evaluationPromise;
      this.audioEvaluationDebug.set(evaluation);

      if (this.isPractice()) {
        this.feedbackEvaluation.set(evaluation);
        this.openFeedbackDrawer();
      } else {
        await this.advanceSessionOrComplete();
      }
    } catch {
      this.loadError.set('Audio submit failed. Please try again.');
    } finally {
      this.transcribingMessage.set('');
      this.isSubmitting.set(false);
    }
  }

  toggleInputMode(mode?: 'text' | 'audio'): void {
    if (mode) {
      this.submissionMode.set(mode);
      return;
    }

    this.submissionMode.update((value) => (value === 'text' ? 'audio' : 'text'));
  }

  async tryAgain(): Promise<void> {
    const question = this.currentQuestion();
    if (!question) {
      return;
    }

    try {
      await firstValueFrom(
        this.api.retryAnswer({
          sessionId: this.sessionId,
          questionId: question.id,
          answerText: this.answerText(),
        })
      );
    } catch {
      this.loadError.set('Retry request failed. You can still continue.');
    }

    this.answerText.set('');
    this.feedbackOpen.set(false);
    this.feedbackEvaluation.set(null);
    this.audioEvaluationDebug.set(null);
    this.idealExpanded.set(false);
    this.idealReveal.set(false);
    this.showFollowUpDialog.set(false);
  }

  async nextQuestion(): Promise<void> {
    this.feedbackOpen.set(false);
    this.feedbackEvaluation.set(null);
    this.audioTranscriptDebug.set(null);
    this.audioEvaluationDebug.set(null);
    this.idealExpanded.set(false);
    this.idealReveal.set(false);
    this.followUpInputOpen.set(false);
    this.showFollowUpDialog.set(false);
    this.followUpText.set('');
    this.followUpState.set(null);
    await this.advanceSessionOrComplete();
  }

  async syncMlWithSession(): Promise<void> {
    this.loadError.set(null);
    try {
      await this.advanceSessionOrComplete();
    } catch {
      this.loadError.set('Could not sync to the latest question. Please refresh the session.');
    }
  }

  toggleHint(): void {
    this.hintOpen.update((isOpen) => !isOpen);
  }

  requestAbandon(): void {
    this.showAbandonConfirm.set(true);
  }

  cancelAbandon(): void {
    this.showAbandonConfirm.set(false);
  }

  async confirmAbandon(): Promise<void> {
    this.showAbandonConfirm.set(false);
    try {
      await firstValueFrom(this.api.abandonSession(this.sessionId));
      this.router.navigate(['/dashboard/interview']);
    } catch {
      this.loadError.set('Could not abandon this session right now.');
    }
  }

  toggleIdeal(): void {
    this.idealExpanded.update((value) => !value);
  }

  toggleIdealReveal(): void {
    this.idealReveal.update((value) => !value);
  }

  openFollowUpInput(): void {
    this.followUpInputOpen.set(true);
    this.showFollowUpDialog.set(true);
    this.followUpState.set(null);
  }

  closeFollowUpInput(): void {
    this.showFollowUpDialog.set(false);
  }

  async submitFollowUp(): Promise<void> {
    const question = this.currentQuestion();
    const parentAnswerId = this.lastAnswerId();
    const answerText = this.followUpText().trim();

    if (!question || !parentAnswerId || !answerText || this.followUpBusy()) {
      return;
    }

    this.followUpBusy.set(true);
    this.followUpState.set(null);

    try {
      const submitted = await firstValueFrom(
        this.api.submitFollowUp({
          sessionId: this.sessionId,
          questionId: question.id,
          parentAnswerId,
          answerText,
        })
      );

      const evaluationPromise = this.waitForEvaluationEvent(submitted.id);
      const followUpEvaluation = await evaluationPromise;
      this.followUpState.set(
        followUpEvaluation.overallScore === null
          ? 'Follow-up submitted.'
          : `Follow-up evaluated: ${followUpEvaluation.overallScore.toFixed(1)} / 10`
      );
      this.followUpText.set('');
      this.showFollowUpDialog.set(false);
    } catch {
      this.followUpState.set('Unable to evaluate follow-up right now.');
    } finally {
      this.followUpBusy.set(false);
    }
  }

  getPhaseClass(type: string | undefined): string {
    switch (type) {
      case 'BEHAVIORAL':
        return 'phase-behavioral';
      case 'TECHNICAL':
        return 'phase-technical';
      case 'SITUATIONAL':
        return 'phase-situational';
      default:
        return 'phase-coding';
    }
  }

  getDifficultyClass(level: string | undefined): string {
    switch (level) {
      case 'BEGINNER':
        return 'difficulty-beginner';
      case 'INTERMEDIATE':
        return 'difficulty-intermediate';
      case 'ADVANCED':
        return 'difficulty-advanced';
      default:
        return 'difficulty-expert';
    }
  }

  getTimerToneClass(): string {
    if (!this.isTest()) {
      return 'tone-practice';
    }

    if (this.countdownLeft() <= 10) {
      return 'tone-danger';
    }

    if (this.countdownLeft() <= 30) {
      return 'tone-warning';
    }

    return 'tone-normal';
  }

  private bootstrapSession(): void {
    this.loadError.set(null);

    firstValueFrom(this.api.getSessionById(this.sessionId))
      .then(async (session) => {
        if (!isCurrentInterviewUser(session.userId)) {
          this.loadError.set('This session does not belong to user #1.');
          this.isLoaded.set(false);
          return;
        }

        const [currentQuestion, questionOrders, answers, evaluations] = await Promise.all([
          firstValueFrom(this.api.getCurrentSessionQuestion(this.sessionId)),
          firstValueFrom(this.api.getSessionQuestionOrder(this.sessionId)),
          firstValueFrom(this.api.getAnswersBySession(this.sessionId)),
          firstValueFrom(this.api.getEvaluationsBySession(this.sessionId)),
        ]);

        this.session.set(session);
        this.questionOrders.set(questionOrders);
        this.restoreSessionState(answers, evaluations);
        this.currentIndex.set(this.resolveCurrentIndex(session, questionOrders, currentQuestion));
        this.isLoaded.set(true);
        this.startElapsedTimer();
        this.configureQuestionTimer();
        this.loadQuestion(currentQuestion);
      })
      .catch(() => {
        this.loadError.set('Failed to load the interview room.');
      });
  }

  private async advanceSessionOrComplete(): Promise<void> {
    try {
      const currentQuestion = await firstValueFrom(this.api.getCurrentSessionQuestion(this.sessionId));
      const [session, questionOrders] = await Promise.all([
        firstValueFrom(this.api.getSessionById(this.sessionId)),
        firstValueFrom(this.api.getSessionQuestionOrder(this.sessionId)),
      ]);

      if (!isCurrentInterviewUser(session.userId)) {
        this.loadError.set('This session does not belong to user #1.');
        this.router.navigate(['/dashboard/interview']);
        return;
      }

      this.session.set(session);
      this.questionOrders.set(questionOrders);
      this.currentIndex.set(this.resolveCurrentIndex(session, questionOrders, currentQuestion));
      this.answerText.set('');
      this.configureQuestionTimer();
      this.loadQuestion(currentQuestion);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        await this.completeAndGenerateReport();
        return;
      }

      this.loadError.set('Could not move to the next question.');
    }
  }

  private async completeAndGenerateReport(): Promise<void> {
    this.reportPreparing.set(true);
    this.reportPreparingMessage.set('Getting the report ready...');

    try {
      const previousStreak = this.streakService.getSnapshot()?.currentStreak ?? 0;

      await this.tryCompleteSessionBestEffort();
      const report = await this.resolveFinalReport(this.sessionId);

      this.reportPreparingMessage.set('Report is ready. Redirecting...');

      if (this.currentUserId) {
        const updatedStreak = await firstValueFrom(this.streakService.refresh(this.currentUserId));
        if (updatedStreak.currentStreak > previousStreak) {
          this.streakCelebrationValue.set(updatedStreak.currentStreak);
          this.showStreakCelebration.set(true);
          await this.sleep(1500);
          this.showStreakCelebration.set(false);
        }
      }

      await this.sleep(REPORT_REDIRECT_DELAY_MS);
      await this.router.navigate(['/dashboard/interview/report', report.id]);
    } catch {
      this.reportPreparingMessage.set('Unexpected issue while preparing the report. Retrying...');

      try {
        const report = await this.resolveFinalReport(this.sessionId);
        await this.sleep(REPORT_REDIRECT_DELAY_MS);
        await this.router.navigate(['/dashboard/interview/report', report.id]);
      } catch {
        this.loadError.set('Report generation is taking longer than expected. Please refresh this page.');
      }
    } finally {
      this.reportPreparing.set(false);
    }
  }

  private async tryCompleteSessionBestEffort(): Promise<void> {
    try {
      await firstValueFrom(this.api.completeSession(this.sessionId));
    } catch {
      // Session may already be completed; continue with report resolution.
    }
  }

  private async resolveFinalReport(sessionId: number): Promise<InterviewReportDto> {
    let attempt = 0;

    while (true) {
      attempt += 1;
      this.reportPreparingMessage.set(`Getting the report ready... (attempt ${attempt})`);

      try {
        const generated = await firstValueFrom(this.api.generateReport(sessionId));
        if (generated?.id) {
          return generated;
        }
      } catch {
        // Continue and try reading existing report by session.
      }

      try {
        const existing = await firstValueFrom(this.api.getReportBySession(sessionId));
        if (existing?.id) {
          return existing;
        }
      } catch {
        // Report can still be in progress.
      }

      await this.sleep(REPORT_READY_POLL_INTERVAL_MS);
    }
  }

  private waitForEvaluationEvent(answerId: number): Promise<AnswerEvaluationDto> {
    if (!this.isWsConnected()) {
      return this.pollEvaluation(answerId);
    }

    const existing = this.pendingEvaluations.get(answerId);
    if (existing) {
      clearTimeout(existing.timeoutRef);
      this.pendingEvaluations.delete(answerId);
    }

    return new Promise((resolve, reject) => {
      const timeoutRef = setTimeout(async () => {
        this.pendingEvaluations.delete(answerId);

        // Single fallback fetch for resilience if an event is missed.
        try {
          const evaluation = await firstValueFrom(this.api.getEvaluationByAnswer(answerId));
          if (evaluation.overallScore !== null) {
            resolve(evaluation);
            return;
          }
        } catch {
          // Ignore fallback failure and reject with timeout below.
        }

        reject(new Error('Evaluation timeout'));
      }, EVALUATION_WAIT_TIMEOUT_MS);

      this.pendingEvaluations.set(answerId, {
        resolve,
        reject,
        timeoutRef,
      });
    });
  }

  private async startEvaluationPolling(answerId: number): Promise<void> {
    this.lastAnswerId.set(answerId);
    this.isSubmitting.set(true);

    try {
      const evaluation = await this.waitForEvaluationEvent(answerId);

      if (this.isPractice()) {
        this.feedbackEvaluation.set(evaluation);
        this.openFeedbackDrawer();
      } else {
        await this.advanceSessionOrComplete();
      }
    } catch {
      // Recovery path: websocket events can be missed or arrive out-of-order.
      try {
        const recovered = await this.pollEvaluation(answerId);
        if (this.isPractice()) {
          this.feedbackEvaluation.set(recovered);
          this.openFeedbackDrawer();
        } else {
          await this.advanceSessionOrComplete();
        }
      } catch {
        this.loadError.set('Unable to evaluate this answer right now.');
      }
    } finally {
      this.isSubmitting.set(false);
    }
  }

  private async pollEvaluation(answerId: number): Promise<AnswerEvaluationDto> {
    for (let attempt = 0; attempt < EVALUATION_POLL_ATTEMPTS; attempt++) {
      try {
        const evaluation = await firstValueFrom(this.api.getEvaluationByAnswer(answerId));
        if (evaluation.overallScore !== null && evaluation.overallScore !== undefined) {
          return evaluation;
        }
      } catch {
        // Ignore transient read errors while backend is still evaluating.
      }

      await this.sleep(EVALUATION_POLL_INTERVAL_MS);
    }

    throw new Error('Evaluation polling timeout');
  }

  private initializeRealtime(): void {
    try {
      this.websocketService.connect();
      this.wsConnectionSub = this.websocketService.isConnected$.subscribe((connected) => {
        this.isWsConnected.set(connected);
      });
      this.wsSessionSub = this.websocketService
        .subscribeToSession(this.sessionId)
        .subscribe((event) => this.handleSessionEvent(event));
    } catch {
      this.isWsConnected.set(false);
    }
  }

  private handleSessionEvent(event: SessionEvent): void {
    if (event.sessionId !== this.sessionId) {
      return;
    }

    if (event.eventType === 'EVALUATION_READY') {
      const payload = event.payload as Partial<AnswerEvaluationDto> | null;
      const answerId = payload?.answerId;

      if (!answerId) {
        return;
      }

      const pending = this.pendingEvaluations.get(answerId);
      if (!pending) {
        return;
      }

      clearTimeout(pending.timeoutRef);
      this.pendingEvaluations.delete(answerId);
      pending.resolve(payload as AnswerEvaluationDto);
      return;
    }

    if (event.eventType === 'ERROR') {
      const message = event.message || 'Live evaluation failed. Please retry.';
      this.rejectPendingEvaluations(message);
      return;
    }

    if (event.eventType === 'FOLLOW_UP' || event.eventType === 'FOLLOW_UP_READY') {
      const payload = (event.payload ?? {}) as Record<string, unknown>;
      const candidate =
        (typeof payload['followUpGenerated'] === 'string' ? payload['followUpGenerated'] : null)
        ?? (typeof payload['followUp'] === 'string' ? payload['followUp'] : null)
        ?? (typeof payload['question'] === 'string' ? payload['question'] : null)
        ?? event.message
        ?? null;

      if (candidate && candidate.trim()) {
        this.mlFollowUpText.set(candidate.trim());
      }
    }
  }

  private rejectPendingEvaluations(reason: string): void {
    this.pendingEvaluations.forEach((pending) => {
      clearTimeout(pending.timeoutRef);
      pending.reject(new Error(reason));
    });

    this.pendingEvaluations.clear();
  }

  private openFeedbackDrawer(): void {
    this.feedbackOpen.set(true);
    this.animatedScore.set(0);
    this.idealExpanded.set(false);
    this.idealReveal.set(false);

    const target = this.feedbackEvaluation()?.overallScore ?? 0;
    const steps = 20;
    const increment = target / steps;
    let currentStep = 0;

    if (this.scoreAnimRef) {
      clearInterval(this.scoreAnimRef);
    }

    this.scoreAnimRef = setInterval(() => {
      currentStep += 1;
      this.animatedScore.set(Math.min(target, Number((increment * currentStep).toFixed(1))));

      if (currentStep >= steps) {
        clearInterval(this.scoreAnimRef!);
        this.scoreAnimRef = null;
      }
    }, 35);
  }

  private resolveCurrentIndex(
    session: InterviewSessionDto,
    questionOrders: SessionQuestionOrderDto[],
    currentQuestion: InterviewQuestionDto
  ): number {
    const fromQuestion = questionOrders.find((order) => order.questionId === currentQuestion.id)?.questionOrder;
    if (fromQuestion !== undefined) {
      return fromQuestion;
    }

    if (session.currentQuestionIndex >= 0 && session.currentQuestionIndex < questionOrders.length) {
      return session.currentQuestionIndex;
    }

    return 0;
  }

  private restoreSessionState(answers: SessionAnswerDto[], evaluations: AnswerEvaluationDto[]): void {
    const latestPrimaryAnswer = [...answers]
      .filter((answer) => !answer.isFollowUp)
      .sort((left, right) => new Date(left.submittedAt ?? 0).getTime() - new Date(right.submittedAt ?? 0).getTime())
      .at(-1);

    this.lastAnswerId.set(latestPrimaryAnswer?.id ?? null);

    if (!this.isPractice() || !latestPrimaryAnswer) {
      return;
    }

    const latestEvaluation = latestPrimaryAnswer.answerEvaluation
      ?? evaluations.find((evaluation) => evaluation.answerId === latestPrimaryAnswer.id)
      ?? null;

    if (latestEvaluation && latestEvaluation.overallScore !== null && latestEvaluation.overallScore !== undefined) {
      this.feedbackEvaluation.set(latestEvaluation);
    }
  }

  private startElapsedTimer(): void {
    if (this.elapsedTimerRef) {
      clearInterval(this.elapsedTimerRef);
    }

    this.elapsedTimerRef = setInterval(() => {
      this.elapsedSeconds.update((value) => value + 1);
    }, 1000);
  }

  private configureQuestionTimer(): void {
    if (this.questionTimerRef) {
      clearInterval(this.questionTimerRef);
      this.questionTimerRef = null;
    }

    if (!this.isTest()) {
      return;
    }

    const currentOrder = this.questionOrders().find((order) => order.questionOrder === this.currentIndex());
    const allotted = currentOrder?.timeAllottedSeconds ?? 120;
    this.countdownTotal.set(allotted);
    this.countdownLeft.set(allotted);

    this.questionTimerRef = setInterval(() => {
      const next = this.countdownLeft() - 1;
      this.countdownLeft.set(Math.max(0, next));

      if (next <= 0) {
        clearInterval(this.questionTimerRef!);
        this.questionTimerRef = null;
        this.submitAnswer(true);
      }
    }, 1000);
  }

  private parseJsonArray(raw: string | null | undefined): string[] {
    if (!raw) {
      return [];
    }

    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.map((entry) => String(entry));
      }
      return [String(parsed)];
    } catch {
      return [raw];
    }
  }

  toClock(seconds: number): string {
    const minutes = Math.floor(seconds / 60);
    const remaining = seconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(remaining).padStart(2, '0')}`;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(resolve, ms);
    });
  }

  private playQuestionAudio(question: InterviewQuestionDto | null): void {
    const audioUrl = question?.ttsAudioUrl;
    if (!audioUrl) {
      return;
    }

    this.ttsService.playFromUrl(audioUrl).catch(() => {
      // Ignore autoplay or playback failures to avoid blocking question flow.
    });
  }

  private clearIntervals(): void {
    if (this.elapsedTimerRef) {
      clearInterval(this.elapsedTimerRef);
    }
    if (this.questionTimerRef) {
      clearInterval(this.questionTimerRef);
    }
    if (this.scoreAnimRef) {
      clearInterval(this.scoreAnimRef);
    }

    this.elapsedTimerRef = null;
    this.questionTimerRef = null;
    this.scoreAnimRef = null;
  }
}