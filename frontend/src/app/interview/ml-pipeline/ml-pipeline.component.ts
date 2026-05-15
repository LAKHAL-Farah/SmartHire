import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom, interval, merge, of, Subject } from 'rxjs';
import { catchError, filter, finalize, startWith, switchMap, take, takeUntil } from 'rxjs/operators';
import { MLScenarioAnswer, PipelineStage } from '../models/ml-scenario-answer.model';
import { MlAnswerService } from '../services/ml-answer.service';
import { MicButtonComponent } from '../../features/front-office/dashboard/interview/components/mic-button/mic-button.component';
import { AnswerService } from '../../features/front-office/dashboard/interview/services/answer.service';
import { InterviewApiService } from '../../features/front-office/dashboard/interview/interview-api.service';
import { SessionAnswerDto } from '../../features/front-office/dashboard/interview/interview.models';
import { TtsService } from '../../shared/services/tts.service';

@Component({
  selector: 'app-ml-pipeline',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    MatIconModule,
    MatChipsModule,
    MatCardModule,
    MatButtonModule,
    MicButtonComponent,
  ],
  templateUrl: './ml-pipeline.component.html',
  styleUrl: './ml-pipeline.component.scss',
})
export class MlPipelineComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly interviewApi = inject(InterviewApiService);
  private readonly ttsService = inject(TtsService);
  private readonly answerService = inject(AnswerService);
  private readonly mlAnswerService = inject(MlAnswerService);
  private readonly apiBase = this.resolveBaseUrl();
  private readonly apiRoot = this.resolveApiRoot(this.apiBase);

  private readonly destroy$ = new Subject<void>();
  private readonly pollStop$ = new Subject<void>();
  private readonly highlightTimeouts: Array<ReturnType<typeof setTimeout>> = [];

  private timerRef: ReturnType<typeof setInterval> | null = null;

  @Input() sessionId = 0;
  @Input() questionId = 0;
  @Input() answerId: number | null = null;
  @Input() questionText: string | null = null;
  @Input() followUpText: string | null = null;

  @Output() answerSubmitted = new EventEmitter<SessionAnswerDto>();
  @Output() resyncRequested = new EventEmitter<void>();

  liveAnswerText = '';
  transcribingMessage = '';
  extractionMessage = 'Submit an answer to start ML concept extraction.';

  isSubmitting = false;
  isPolling = false;
  hasSubmittedForCurrentQuestion = false;
  elapsedSeconds = 0;

  latestMlAnswer: MLScenarioAnswer | null = null;

  readonly stages: PipelineStage[] = [
    {
      id: 'ingestion',
      label: 'Data Ingestion',
      icon: 'cloud_download',
      keywords: ['data', 'dataset', 'ingest', 'collect', 'raw data'],
      highlighted: false,
      order: 1,
    },
    {
      id: 'preprocessing',
      label: 'Preprocessing',
      icon: 'tune',
      keywords: ['clean', 'preprocess', 'normalize', 'impute', 'missing'],
      highlighted: false,
      order: 2,
    },
    {
      id: 'features',
      label: 'Feature Engineering',
      icon: 'build',
      keywords: ['feature', 'engineering', 'transform', 'encode', 'scaling'],
      highlighted: false,
      order: 3,
    },
    {
      id: 'training',
      label: 'Model Training',
      icon: 'model_training',
      keywords: ['train', 'model', 'fit', 'algorithm', 'hyperparameter'],
      highlighted: false,
      order: 4,
    },
    {
      id: 'evaluation',
      label: 'Evaluation',
      icon: 'analytics',
      keywords: ['evaluate', 'accuracy', 'f1', 'auc', 'precision', 'recall'],
      highlighted: false,
      order: 5,
    },
    {
      id: 'deployment',
      label: 'Deployment',
      icon: 'rocket_launch',
      keywords: ['deploy', 'api', 'serve', 'production', 'docker', 'endpoint'],
      highlighted: false,
      order: 6,
    },
    {
      id: 'monitoring',
      label: 'Monitoring',
      icon: 'monitor_heart',
      keywords: ['monitor', 'drift', 'alert', 'retrain', 'degrade', 'metrics'],
      highlighted: false,
      order: 7,
    },
  ];

  ngOnInit(): void {
    this.timerRef = setInterval(() => {
      this.elapsedSeconds += 1;
    }, 1000);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['questionId'] && !changes['questionId'].firstChange) {
      this.resetForNewQuestion();
    }
  }

  ngOnDestroy(): void {
    if (this.timerRef) {
      clearInterval(this.timerRef);
      this.timerRef = null;
    }

    this.clearHighlightTimers();
    this.pollStop$.next();
    this.pollStop$.complete();
    this.destroy$.next();
    this.destroy$.complete();
  }

  async submitTypedAnswer(): Promise<void> {
    const answer = this.liveAnswerText.trim();
    if (!answer || this.isSubmitting || this.hasSubmittedForCurrentQuestion || !this.sessionId || !this.questionId) {
      if (this.hasSubmittedForCurrentQuestion) {
        this.transcribingMessage = 'Answer already submitted for this question. Loading latest question...';
        this.resyncRequested.emit();
      }
      return;
    }

    this.isSubmitting = true;
    this.transcribingMessage = 'Submitting your typed answer...';

    try {
      const submitted = await firstValueFrom(
        this.answerService.submitTextAnswer(
          this.sessionId,
          this.questionId,
          answer,
          this.buildPipelineCodeAnswer()
        )
      );

      this.hasSubmittedForCurrentQuestion = true;
      this.transcribingMessage = 'Answer submitted. Waiting for scoring...';
      this.answerSubmitted.emit(submitted);
    } catch (error) {
      if (this.isLikelyStaleQuestionSubmit(error)) {
        this.transcribingMessage = 'Question already advanced. Syncing to the latest question...';
        this.resyncRequested.emit();
      } else {
        this.transcribingMessage = 'Submission failed. Please try again.';
      }
    } finally {
      this.isSubmitting = false;
    }
  }

  async onAudioReady(audioBlob: Blob): Promise<void> {
    if (this.isSubmitting || this.hasSubmittedForCurrentQuestion || !this.sessionId || !this.questionId) {
      if (this.hasSubmittedForCurrentQuestion) {
        this.transcribingMessage = 'Answer already submitted for this question. Loading latest question...';
        this.resyncRequested.emit();
      }
      return;
    }

    this.isSubmitting = true;
    this.transcribingMessage = 'Transcribing your spoken answer...';

    try {
      const submitted = await firstValueFrom(
        this.answerService.submitAudioAnswer(
          this.sessionId,
          this.questionId,
          audioBlob,
          this.buildPipelineCodeAnswer()
        )
      );

      if (submitted.answerText) {
        this.liveAnswerText = submitted.answerText;
      }

      this.hasSubmittedForCurrentQuestion = true;
      this.transcribingMessage = 'Audio submitted. Waiting for scoring...';
      this.answerSubmitted.emit(submitted);
    } catch (error) {
      if (this.isLikelyStaleQuestionSubmit(error)) {
        this.transcribingMessage = 'Question already advanced. Syncing to the latest question...';
        this.resyncRequested.emit();
      } else {
        this.transcribingMessage = 'Audio submission failed. Please retry.';
      }
    } finally {
      this.isSubmitting = false;
    }
  }

  onAnswerSubmitted(answerId: number): void {
    if (!answerId) {
      return;
    }

    this.answerId = answerId;
  this.hasSubmittedForCurrentQuestion = true;
    this.extractionMessage = 'Triggering ML concept extraction...';

    this.mlAnswerService.triggerExtraction(answerId).pipe(
      catchError(() => of(void 0))
    ).subscribe(() => {
      this.extractionMessage = 'Extraction requested. Waiting for ML concepts...';
    });

    this.startPolling(answerId);
  }

  highlightMatchedStages(mlAnswer: MLScenarioAnswer): void {
    const conceptPool = this.collectConceptPool(mlAnswer);
    this.clearHighlightTimers();

    for (const stage of this.stages) {
      stage.highlighted = false;
    }

    const matchedStages = this.stages.filter((stage) => this.stageMatches(stage, conceptPool));

    matchedStages.forEach((stage, index) => {
      const timeout = setTimeout(() => {
        stage.highlighted = true;
      }, index * 200);
      this.highlightTimeouts.push(timeout);
    });
  }

  onStageDrop(event: CdkDragDrop<PipelineStage[]>): void {
    moveItemInArray(this.stages, event.previousIndex, event.currentIndex);

    this.stages.forEach((stage, index) => {
      stage.order = index + 1;
    });
  }

  getStageConnectorClass(index: number): string {
    const current = this.stages[index];
    const next = this.stages[index + 1];

    if (current?.highlighted && next?.highlighted) {
      return 'connector--highlighted';
    }

    return 'connector--default';
  }

  get mentionedCount(): number {
    return this.stages.filter((stage) => stage.highlighted).length;
  }

  get followUpDisplay(): string | null {
    return this.latestMlAnswer?.followUpGenerated ?? this.followUpText;
  }

  get scoreLabel(): string {
    const score = this.latestMlAnswer?.mlScore;
    if (score === null || score === undefined) {
      return '--';
    }

    return Number(score).toFixed(1);
  }

  playFollowUpTts(): void {
    const text = this.followUpDisplay;
    if (!text?.trim()) {
      return;
    }

    this.http.post<{ audioUrl?: string }>(`${this.apiRoot}/audio/tts/speak`, { text: text.trim() }).subscribe({
      next: (response) => {
        const rawAudioUrl = (response?.audioUrl ?? '').trim();
        if (!rawAudioUrl) {
          return;
        }

        const absoluteAudioUrl = this.interviewApi.resolveBackendAssetUrl(rawAudioUrl);
        const deleteUrl = this.interviewApi.resolveBackendAssetUrl(rawAudioUrl);
        let cleaned = false;

        const cleanup = (): void => {
          if (cleaned) {
            return;
          }
          cleaned = true;
          this.http.delete(deleteUrl).subscribe({ error: () => undefined });
        };

        this.ttsService.playAbsoluteUrl(absoluteAudioUrl)
          .then(() => cleanup())
          .catch(() => cleanup());
      },
      error: () => {
        // Best effort: follow-up TTS should never block the interview flow.
      },
    });
  }

  toClock(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  private startPolling(answerId: number): void {
    this.pollStop$.next();
    this.isPolling = true;

    interval(3000).pipe(
      startWith(0),
      take(10),
      takeUntil(merge(this.pollStop$, this.destroy$)),
      switchMap(() =>
        this.mlAnswerService.getMLAnswer(answerId).pipe(
          catchError(() => of(null))
        )
      ),
      filter((result): result is MLScenarioAnswer => !!result && result.mlScore !== null),
      take(1),
      finalize(() => {
        this.isPolling = false;
      })
    ).subscribe({
      next: (mlAnswer) => {
        this.latestMlAnswer = mlAnswer;
        this.extractionMessage = 'ML concepts extracted successfully.';
        this.highlightMatchedStages(mlAnswer);
        this.pollStop$.next();
      },
      complete: () => {
        if (!this.latestMlAnswer || this.latestMlAnswer.answerId !== answerId) {
          this.extractionMessage = 'No ML concept score yet. You can continue to the next question.';
        }
      },
    });
  }

  private collectConceptPool(mlAnswer: MLScenarioAnswer): string[] {
    const collected: string[] = [];

    const pushIfPresent = (value: string | null): void => {
      if (value && value.trim()) {
        collected.push(value.toLowerCase());
      }
    };

    pushIfPresent(mlAnswer.modelChosen);
    pushIfPresent(mlAnswer.deployment);
    pushIfPresent(mlAnswer.dataPreprocessing);
    pushIfPresent(mlAnswer.evaluationStrategy);

    for (const feature of mlAnswer.features ?? []) {
      pushIfPresent(feature);
    }

    for (const metric of mlAnswer.metrics ?? []) {
      pushIfPresent(metric);
    }

    return collected;
  }

  private stageMatches(stage: PipelineStage, conceptPool: string[]): boolean {
    return stage.keywords.some((keyword) => {
      const normalizedKeyword = keyword.toLowerCase();
      return conceptPool.some((concept) => concept.includes(normalizedKeyword) || normalizedKeyword.includes(concept));
    });
  }

  private buildPipelineCodeAnswer(): string {
    const pipelineOrder = this.stages.map((stage) => stage.id);
    return `PIPELINE:${JSON.stringify(pipelineOrder)}`;
  }

  private clearHighlightTimers(): void {
    for (const timeoutRef of this.highlightTimeouts) {
      clearTimeout(timeoutRef);
    }

    this.highlightTimeouts.length = 0;
  }

  private resetForNewQuestion(): void {
    this.pollStop$.next();
    this.isPolling = false;
    this.isSubmitting = false;
    this.hasSubmittedForCurrentQuestion = false;
    this.latestMlAnswer = null;
    this.transcribingMessage = '';
    this.extractionMessage = 'Submit an answer to start ML concept extraction.';
    this.clearHighlightTimers();
    for (const stage of this.stages) {
      stage.highlighted = false;
    }
  }

  private isLikelyStaleQuestionSubmit(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse)) {
      return false;
    }

    return [400, 404, 409, 410, 500].includes(error.status);
  }

  private resolveBaseUrl(): string {
    const configured = (globalThis.localStorage?.getItem('smarthire.interviewApiBaseUrl') ?? '').trim();
    if (configured) {
      return configured.replace(/\/+$/, '');
    }


    return '/interview-service/api/v1';
  }

  private resolveApiRoot(apiBase: string): string {
    if (!apiBase) {
      return '';
    }

    return apiBase.replace(/\/api\/v1\/?$/, '');
  }
}
