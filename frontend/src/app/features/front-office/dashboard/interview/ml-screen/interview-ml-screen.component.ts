import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

interface MLQuestionView {
  domain: string;
  category: string;
  questionText: string;
}

interface PipelineStage {
  key: 'ingestion' | 'preprocessing' | 'model' | 'training' | 'deployment';
  label: string;
}

@Component({
  selector: 'app-interview-ml-screen',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './interview-ml-screen.component.html',
  styleUrl: './interview-ml-screen.component.scss',
})
export class InterviewMlScreenComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private elapsedTimerRef: ReturnType<typeof setInterval> | null = null;
  private highlightDebounceRef: ReturnType<typeof setTimeout> | null = null;

  readonly sessionId = signal<number | null>(null);
  readonly elapsedSeconds = signal(0);
  readonly currentIndex = signal(0);
  readonly totalQuestions = signal(8);
  readonly mode = signal<'PRACTICE' | 'TEST'>('PRACTICE');

  readonly question = signal<MLQuestionView>({
    domain: 'Machine Learning',
    category: 'Model Selection',
    questionText:
      'Design an end-to-end ML solution for churn prediction: from data ingestion to deployment, including monitoring strategy.',
  });

  readonly answerText = signal('');
  readonly hintOpen = signal(false);
  readonly feedbackOpen = signal(false);

  readonly stages: PipelineStage[] = [
    { key: 'ingestion', label: 'Data Ingestion' },
    { key: 'preprocessing', label: 'Preprocessing' },
    { key: 'model', label: 'Model Selection' },
    { key: 'training', label: 'Training & Eval' },
    { key: 'deployment', label: 'Deployment' },
  ];

  readonly highlightedStages = signal<Record<PipelineStage['key'], boolean>>({
    ingestion: false,
    preprocessing: false,
    model: false,
    training: false,
    deployment: false,
  });

  readonly extractedModel = signal<string | null>(null);
  readonly extractedMetrics = signal<string | null>(null);
  readonly extractedDeployment = signal<string | null>(null);

  readonly timerDisplay = computed(() => this.toClock(this.elapsedSeconds()));
  readonly progressPercent = computed(() => ((this.currentIndex() + 1) / this.totalQuestions()) * 100);

  ngOnInit(): void {
    const parsed = Number(this.route.snapshot.paramMap.get('id'));
    if (Number.isFinite(parsed)) {
      this.sessionId.set(parsed);
    }

    this.elapsedTimerRef = setInterval(() => {
      this.elapsedSeconds.update((value) => value + 1);
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.elapsedTimerRef) {
      clearInterval(this.elapsedTimerRef);
      this.elapsedTimerRef = null;
    }

    if (this.highlightDebounceRef) {
      clearTimeout(this.highlightDebounceRef);
      this.highlightDebounceRef = null;
    }
  }

  updateAnswer(value: string): void {
    this.answerText.set(value);

    if (this.highlightDebounceRef) {
      clearTimeout(this.highlightDebounceRef);
    }

    this.highlightDebounceRef = setTimeout(() => {
      this.applyHighlights(this.answerText());
    }, 300);
  }

  toggleHint(): void {
    this.hintOpen.update((value) => !value);
  }

  submitAnswer(): void {
    if (!this.answerText().trim()) {
      return;
    }

    this.feedbackOpen.set(true);

    const text = this.answerText().toLowerCase();
    this.extractedModel.set(text.includes('xgboost') ? 'XGBoost' : text.includes('bert') ? 'BERT' : null);
    this.extractedMetrics.set(text.includes('f1') || text.includes('auc') ? 'F1, AUC' : null);
    this.extractedDeployment.set(text.includes('docker') || text.includes('api') ? 'Dockerized API service' : null);
  }

  isStageHighlighted(stageKey: PipelineStage['key']): boolean {
    return this.highlightedStages()[stageKey];
  }

  private applyHighlights(text: string): void {
    const lower = text.toLowerCase();

    this.highlightedStages.set({
      ingestion: /(data|dataset|ingest)/.test(lower),
      preprocessing: /(preprocess|clean|normalize|feature)/.test(lower),
      model: /(model|algorithm|xgboost|bert|lstm)/.test(lower),
      training: /(train|epoch|loss|accuracy|f1|auc)/.test(lower),
      deployment: /(deploy|production|api|docker|inference)/.test(lower),
    });
  }

  private toClock(seconds: number): string {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
}
